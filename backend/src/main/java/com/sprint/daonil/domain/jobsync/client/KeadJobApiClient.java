package com.sprint.daonil.domain.jobsync.client;

import com.sprint.daonil.domain.jobsync.config.KeadApiProperties;
import com.sprint.daonil.domain.jobsync.dto.KeadFetchResult;
import com.sprint.daonil.domain.jobsync.dto.KeadJobItem;
import com.sprint.daonil.domain.jobsync.dto.KeadJobPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 한국장애인고용공단 '장애인 구인 실시간 현황' API 클라이언트.
 *
 * 보안 주의: 요청 URL에 인증키가 들어 있으므로 URL을 로그·예외 메시지에 절대 출력하지 않는다.
 * (RestClient 예외의 원문 메시지에는 URL이 포함되므로, 예외를 그대로 전파하지 않고 KeadApiException으로 바꾼다.)
 */
@Slf4j
@Component
public class KeadJobApiClient {

    private final KeadApiProperties props;
    private final KeadJobXmlParser parser;
    private final RestClient restClient;

    @Autowired
    public KeadJobApiClient(KeadApiProperties props, KeadJobXmlParser parser, RestClient.Builder builder) {
        this(props, parser, builder.requestFactory(requestFactory(props)).build());
    }

    /** 테스트에서 RestClient를 직접 주입하기 위한 생성자 */
    KeadJobApiClient(KeadApiProperties props, KeadJobXmlParser parser, RestClient restClient) {
        this.props = props;
        this.parser = parser;
        this.restClient = restClient;
    }

    private static SimpleClientHttpRequestFactory requestFactory(KeadApiProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.getConnectTimeout().toMillis());
        factory.setReadTimeout((int) props.getReadTimeout().toMillis());
        return factory;
    }

    /** 한 페이지 조회. 오류 응답(resultCode != 0000)이면 예외. */
    public KeadJobPage fetchPage(int pageNo, int numOfRows) {
        URI uri = buildUri(pageNo, numOfRows);
        byte[] body = call(uri, pageNo);
        KeadJobPage page = parser.parse(body);
        if (!page.success()) {
            throw new KeadApiException("KEAD API 오류 응답 (page=" + pageNo
                    + ", code=" + page.resultCode() + ", msg=" + page.resultMsg() + ")");
        }
        return page;
    }

    /** 전체 페이지를 순서대로 조회. 수집 건수와 totalCount 비교는 결과의 complete()로 확인한다. */
    public KeadFetchResult fetchAll() {
        List<KeadJobItem> all = new ArrayList<>();
        int totalCount = 0;
        for (int pageNo = 1; pageNo <= props.getMaxPages(); pageNo++) {
            KeadJobPage page = fetchPage(pageNo, props.getPageSize());
            totalCount = page.totalCount();
            all.addAll(page.items());
            if (page.items().isEmpty() || all.size() >= totalCount) {
                break;
            }
        }
        log.info("KEAD 공고 수집: totalCount={}, fetched={}", totalCount, all.size());
        return new KeadFetchResult(totalCount, all);
    }

    private URI buildUri(int pageNo, int numOfRows) {
        String key = props.getKey();
        if (key == null || key.isBlank()) {
            throw new KeadApiException("KEAD_API_KEY 환경변수가 설정되지 않았습니다.");
        }
        key = key.trim();
        // 포털의 Encoding 키(%2F …)는 그대로, Decoding 키(/ + =)는 한 번만 인코딩 → 이중 인코딩 방지
        String encodedKey = key.contains("%") ? key : URLEncoder.encode(key, StandardCharsets.UTF_8);
        try {
            return URI.create(props.getBaseUrl() + props.getListPath()
                    + "?serviceKey=" + encodedKey
                    + "&pageNo=" + pageNo
                    + "&numOfRows=" + numOfRows);
        } catch (IllegalArgumentException e) {
            // URI.create 의 예외 메시지에는 입력 문자열(인증키 포함)이 들어 있으므로 원인을 숨기고 새 예외로 바꾼다
            throw new KeadApiException("KEAD 요청 URL을 만들 수 없습니다 (인증키 형식을 확인하세요)");
        }
    }

    private byte[] call(URI uri, int pageNo) {
        int maxAttempts = Math.max(1, props.getMaxAttempts());
        for (int attempt = 1; ; attempt++) {
            try {
                byte[] body = restClient.get()
                        .uri(uri)
                        .accept(MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.ALL)
                        .retrieve()
                        .body(byte[].class);
                if (body == null || body.length == 0) {
                    throw new KeadApiException("KEAD 빈 응답 (page=" + pageNo + ")");
                }
                return body;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (e.getStatusCode().is5xxServerError() && attempt < maxAttempts) {
                    log.warn("KEAD API {} 응답, 재시도 {}/{} (page={})", status, attempt, maxAttempts, pageNo);
                    backoff(attempt);
                    continue;
                }
                throw new KeadApiException("KEAD API HTTP " + status + " (page=" + pageNo + ")");
            } catch (ResourceAccessException e) {
                if (attempt < maxAttempts) {
                    log.warn("KEAD API 네트워크 오류({}), 재시도 {}/{} (page={})",
                            e.getClass().getSimpleName(), attempt, maxAttempts, pageNo);
                    backoff(attempt);
                    continue;
                }
                throw new KeadApiException("KEAD API 네트워크 오류 (page=" + pageNo + ")");
            } catch (RestClientException e) {
                throw new KeadApiException("KEAD API 호출 실패: " + e.getClass().getSimpleName() + " (page=" + pageNo + ")");
            }
        }
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(1000L * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new KeadApiException("KEAD API 재시도 대기 중 중단됨");
        }
    }
}
