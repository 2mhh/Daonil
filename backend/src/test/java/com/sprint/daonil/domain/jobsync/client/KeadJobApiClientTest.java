package com.sprint.daonil.domain.jobsync.client;

import com.sprint.daonil.domain.jobsync.config.KeadApiProperties;
import com.sprint.daonil.domain.jobsync.dto.KeadFetchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 네트워크·인증키 없이 동작하는 단위 테스트 (MockRestServiceServer) */
class KeadJobApiClientTest {

    private static final String ENCODED_KEY = "abc%2Fdef%3D%3D";   // 포털의 Encoding 키 형태

    private KeadApiProperties props;
    private MockRestServiceServer server;
    private KeadJobApiClient client;

    @BeforeEach
    void setUp() {
        props = new KeadApiProperties();
        props.setKey(ENCODED_KEY);
        props.setPageSize(2);
        props.setMaxAttempts(2);

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KeadJobApiClient(props, new KeadJobXmlParser(), builder.build());
    }

    private static byte[] xml(int total, int pageNo, int rows, String... jobNames) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?>"
                + "<response><header><resultCode>0000</resultCode><resultMsg>OK</resultMsg></header><body><items>");
        for (String name : jobNames) {
            sb.append("<item><jobNm>").append(name).append("</jobNm></item>");
        }
        sb.append("</items><numOfRows>").append(rows).append("</numOfRows><pageNo>").append(pageNo)
                .append("</pageNo><totalCount>").append(total).append("</totalCount></body></response>");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void 모든_페이지를_모아_totalCount와_일치하면_complete다() {
        server.expect(requestTo(containsString("pageNo=1"))).andRespond(withSuccess(xml(3, 1, 2, "가", "나"), MediaType.APPLICATION_XML));
        server.expect(requestTo(containsString("pageNo=2"))).andRespond(withSuccess(xml(3, 2, 2, "다"), MediaType.APPLICATION_XML));

        KeadFetchResult result = client.fetchAll();

        assertThat(result.items()).hasSize(3);
        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.complete()).isTrue();
        server.verify();
    }

    @Test
    void 수집_건수가_totalCount보다_적으면_complete가_아니다() {
        server.expect(requestTo(containsString("pageNo=1"))).andRespond(withSuccess(xml(5, 1, 2, "가", "나"), MediaType.APPLICATION_XML));
        server.expect(requestTo(containsString("pageNo=2"))).andRespond(withSuccess(xml(5, 2, 2), MediaType.APPLICATION_XML));

        KeadFetchResult result = client.fetchAll();

        assertThat(result.items()).hasSize(2);
        assertThat(result.complete()).isFalse();
    }

    @Test
    void Encoding_키는_이중_인코딩하지_않는다() {
        server.expect(requestTo(containsString("serviceKey=" + ENCODED_KEY + "&")))
                .andRespond(withSuccess(xml(1, 1, 2, "가"), MediaType.APPLICATION_XML));

        client.fetchPage(1, 2);

        server.verify();
    }

    @Test
    void Decoding_키는_한번만_인코딩한다() {
        props.setKey("abc/def==");
        server.expect(requestTo(containsString("serviceKey=" + ENCODED_KEY + "&")))
                .andRespond(withSuccess(xml(1, 1, 2, "가"), MediaType.APPLICATION_XML));

        client.fetchPage(1, 2);

        server.verify();
    }

    @Test
    void 서버_오류는_재시도_후_성공한다() {
        server.expect(requestTo(containsString("pageNo=1"))).andRespond(withServerError());
        server.expect(requestTo(containsString("pageNo=1"))).andRespond(withSuccess(xml(1, 1, 2, "가"), MediaType.APPLICATION_XML));

        assertThat(client.fetchPage(1, 2).items()).hasSize(1);
        server.verify();
    }

    @Test
    void 클라이언트_오류는_재시도하지_않고_키를_노출하지_않는다() {
        server.expect(requestTo(containsString("pageNo=1"))).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.fetchPage(1, 2))
                .isInstanceOf(KeadApiException.class)
                .hasMessageContaining("401")
                .hasMessageNotContaining("abc");
        server.verify();   // 요청은 1번뿐
    }

    @Test
    void 오류_응답코드면_예외이며_키가_메시지에_없다() {
        String error = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><OpenAPI_ServiceResponse><cmmMsgHeader>"
                + "<errMsg>SERVICE ERROR</errMsg><returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>"
                + "<returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>";
        server.expect(requestTo(containsString("pageNo=1")))
                .andRespond(withSuccess(error.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> client.fetchPage(1, 2))
                .isInstanceOf(KeadApiException.class)
                .hasMessageContaining("code=30")
                .hasMessageNotContaining("abc");
    }

    @Test
    void 인증키_형식이_잘못돼_URL을_만들_수_없어도_키가_예외에_노출되지_않는다() {
        props.setKey("abc%zz");   // 잘못된 퍼센트 인코딩 → URI.create 가 입력 문자열을 담은 예외를 던진다

        assertThatThrownBy(() -> client.fetchPage(1, 2))
                .isInstanceOf(KeadApiException.class)
                .hasMessageContaining("인증키 형식")
                .hasMessageNotContaining("abc")
                .hasNoCause();
        server.verify();   // 요청 0번
    }

    @Test
    void 인증키가_없으면_호출하지_않고_예외() {
        props.setKey("");

        assertThatThrownBy(() -> client.fetchPage(1, 2))
                .isInstanceOf(KeadApiException.class)
                .hasMessageContaining("KEAD_API_KEY");
        server.verify();   // 요청 0번
    }
}
