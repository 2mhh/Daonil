package com.sprint.daonil.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** CORS 허용 출처가 환경변수(app.cors.allowed-origins)로 바뀐 값을 그대로 쓰는지 확인한다. */
class SecurityConfigCorsTest {

    private CorsConfiguration corsFor(String... origins) {
        SecurityConfig config = new SecurityConfig(
                mock(JwtAuthenticationFilter.class),
                mock(OAuth2AuthenticationSuccessHandler.class),
                mock(OAuth2AuthenticationFailureHandler.class));
        ReflectionTestUtils.setField(config, "allowedOrigins", origins);

        CorsConfigurationSource source = config.corsConfigurationSource();
        return source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/jobs"));
    }

    @Test
    void 설정한_출처만_허용한다() {
        CorsConfiguration cors = corsFor("https://www.example.com", "http://203.0.113.5");

        assertThat(cors.getAllowedOrigins()).containsExactly("https://www.example.com", "http://203.0.113.5");
        assertThat(cors.checkOrigin("https://www.example.com")).isEqualTo("https://www.example.com");
        assertThat(cors.checkOrigin("http://localhost:5173")).isNull();     // 개발용 주소는 더 이상 허용되지 않는다
        assertThat(cors.checkOrigin("https://evil.example.org")).isNull();
    }

    @Test
    void 인증_정보를_함께_보내는_요청을_허용한다() {
        assertThat(corsFor("http://localhost:5173").getAllowCredentials()).isTrue();
    }
}
