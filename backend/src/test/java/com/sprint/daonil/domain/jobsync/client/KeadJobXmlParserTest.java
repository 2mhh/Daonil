package com.sprint.daonil.domain.jobsync.client;

import com.sprint.daonil.domain.jobsync.dto.KeadJobItem;
import com.sprint.daonil.domain.jobsync.dto.KeadJobPage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeadJobXmlParserTest {

    private final KeadJobXmlParser parser = new KeadJobXmlParser();

    private byte[] load(String path) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertThat(in).as("테스트 리소스 " + path).isNotNull();
            return in.readAllBytes();
        }
    }

    @Test
    void 정상_응답을_파싱한다() throws IOException {
        KeadJobPage page = parser.parse(load("/kead/job_list_env2_sample.xml"));

        assertThat(page.success()).isTrue();
        assertThat(page.totalCount()).isEqualTo(239);
        assertThat(page.pageNo()).isEqualTo(1);
        assertThat(page.numOfRows()).isEqualTo(2);
        assertThat(page.items()).hasSize(2);

        KeadJobItem first = page.items().get(0);
        assertThat(first.get("busplaName")).startsWith("쿠팡풀필먼트서비스");
        assertThat(first.get("salary")).isEqualTo("1,080,000");
        assertThat(first.get("salaryType")).isEqualTo("월급");
        assertThat(first.get("termDate")).isEqualTo("2026-11-17~2026-12-01");
        assertThat(first.get("envEyesight")).isEqualTo("아주 작은 글씨를 읽을 수 있음");
    }

    @Test
    void 없는_필드는_null을_반환한다() throws IOException {
        KeadJobPage page = parser.parse(load("/kead/job_list_env2_sample.xml"));

        KeadJobItem second = page.items().get(1);
        assertThat(second.get("envEyesight")).isNull();   // 작업환경 정보가 없는 공고
        assertThat(second.get("reqMajor")).isNull();
        assertThat(second.get("jobNm")).isEqualTo("거리 및 공공장소 청소원(환경 미화원)");
    }

    @Test
    void 인증_오류_응답은_실패로_파싱된다() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><OpenAPI_ServiceResponse><cmmMsgHeader>"
                + "<errMsg>SERVICE ERROR</errMsg><returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>"
                + "<returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>";

        KeadJobPage page = parser.parse(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(page.success()).isFalse();
        assertThat(page.resultCode()).isEqualTo("30");
        assertThat(page.resultMsg()).isEqualTo("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
        assertThat(page.items()).isEmpty();
    }

    @Test
    void DOCTYPE이_포함된_XML은_거부한다() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE a [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><response>&x;</response>";

        assertThatThrownBy(() -> parser.parse(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(KeadApiException.class);
    }

    @Test
    void 깨진_XML은_예외를_던진다() {
        assertThatThrownBy(() -> parser.parse("<response><body>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(KeadApiException.class);
    }
}
