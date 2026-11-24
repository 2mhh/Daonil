package com.sprint.daonil.domain.jobsync.client;

import com.sprint.daonil.domain.jobsync.config.KeadApiProperties;
import com.sprint.daonil.domain.jobsync.dto.KeadFetchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 API를 호출하는 확인용 테스트.
 * 환경변수 KEAD_API_KEY가 설정된 경우에만 실행된다(없으면 건너뜀). 키는 출력하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "KEAD_API_KEY", matches = ".+")
class KeadJobApiClientLiveTest {

    @Test
    void 실제_API에서_전체_공고를_수집한다() {
        KeadApiProperties props = new KeadApiProperties();
        props.setKey(System.getenv("KEAD_API_KEY"));
        KeadJobApiClient client = new KeadJobApiClient(props, new KeadJobXmlParser(), RestClient.builder());

        KeadFetchResult result = client.fetchAll();

        System.out.println("[LIVE] totalCount=" + result.totalCount() + ", fetched=" + result.items().size()
                + ", complete=" + result.complete());
        assertThat(result.totalCount()).isPositive();
        assertThat(result.complete()).isTrue();
        assertThat(result.items().get(0).get("jobNm")).isNotNull();
    }
}
