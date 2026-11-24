package com.sprint.daonil.domain.jobsync.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 한국장애인고용공단 '장애인 구인 실시간 현황' API 설정.
 *
 * 환경변수로 덮어쓴다. (예: KEAD_API_KEY → kead.api.key)
 *  - KEAD_API_KEY       : 공공데이터포털 인증키 (필수, 코드·로그·저장소에 넣지 않는다)
 *  - KEAD_API_BASE_URL  : 기본값 사용 가능
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "kead.api")
public class KeadApiProperties {

    /** 인증키. 포털의 Encoding 키(%2F …)와 Decoding 키 모두 허용한다. */
    private String key = "";

    private String baseUrl = "https://apis.data.go.kr/B552583/job2";

    /** 작업환경 포함 기능 */
    private String listPath = "/job_list_env2";

    /** 한 번에 가져올 건수 */
    private int pageSize = 100;

    /** 무한 반복 방지용 최대 페이지 수 */
    private int maxPages = 50;

    /** 일시적 오류(5xx, 네트워크) 재시도 포함 총 시도 횟수 */
    private int maxAttempts = 3;

    private Duration connectTimeout = Duration.ofSeconds(10);

    private Duration readTimeout = Duration.ofSeconds(30);
}
