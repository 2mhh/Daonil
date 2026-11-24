package com.sprint.daonil.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** 소셜 로그인 실패 시 돌아갈 프론트 주소가 환경변수(app.frontend-url) 값을 쓰는지 확인한다. */
class OAuth2HandlerRedirectTest {

    @Test
    void 로그인_실패_핸들러는_설정한_프론트_주소로_이동한다() throws Exception {
        OAuth2AuthenticationFailureHandler handler = new OAuth2AuthenticationFailureHandler();
        ReflectionTestUtils.setField(handler, "frontendUrl", "https://www.example.com/");   // 끝의 / 는 제거되어야 한다
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "denied"));

        assertThat(response.getRedirectedUrl()).startsWith("https://www.example.com/login?error=");
        assertThat(response.getRedirectedUrl()).doesNotContain("localhost");
    }

    @Test
    void 로그인_성공_핸들러의_오류_경로도_설정한_프론트_주소로_이동한다() throws Exception {
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler();
        ReflectionTestUtils.setField(handler, "frontendUrl", "http://203.0.113.5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // 인증 정보가 없으면 처리 중 예외가 나고, 핸들러는 오류 메시지와 함께 프론트 로그인 화면으로 보낸다
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, null);

        assertThat(response.getRedirectedUrl()).startsWith("http://203.0.113.5/login?error=");
        assertThat(response.getRedirectedUrl()).doesNotContain("localhost");
    }
}
