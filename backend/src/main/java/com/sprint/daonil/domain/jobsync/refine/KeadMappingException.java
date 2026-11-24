package com.sprint.daonil.domain.jobsync.refine;

/** 원본(Raw)을 서비스 데이터로 변환할 수 없을 때 (필수 값 누락 등) */
public class KeadMappingException extends RuntimeException {

    public KeadMappingException(String message) {
        super(message);
    }
}
