package com.sprint.daonil.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    /** 로그인 실패 후 돌아갈 프론트엔드 주소. 환경변수 APP_FRONTEND_URL (기본 http://localhost:5173) */
    @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {

        String errorMessage = "OAuth2 로그인 실패";

        if (exception instanceof OAuth2AuthenticationException) {
            OAuth2AuthenticationException oauth2Exception = (OAuth2AuthenticationException) exception;
            errorMessage = oauth2Exception.getError().getErrorCode() + ": " + oauth2Exception.getMessage();
        } else {
            errorMessage = exception.getMessage();
        }

        log.error("OAuth2 인증 실패: {}", errorMessage, exception);

        // 프론트엔드로 에러 메시지와 함께 리다이렉트
        this.setDefaultFailureUrl(frontendUrl.replaceAll("/+$", "") + "/login?error=" + java.net.URLEncoder.encode(errorMessage, "UTF-8"));

        super.onAuthenticationFailure(request, response, exception);
    }
}

