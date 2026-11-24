package com.sprint.daonil.domain.jobsync.client;

/**
 * KEAD API 호출·응답 처리 실패.
 * 주의: 요청 URL에는 인증키가 들어 있으므로 메시지·원인(cause)에 URL을 담지 않는다.
 */
public class KeadApiException extends RuntimeException {

    public KeadApiException(String message) {
        super(message);
    }
}
