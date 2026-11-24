package com.sprint.daonil.domain.jobsync.entity;

/** Raw 데이터의 정제 상태 */
public enum RawStatus {
    /** 새로 들어왔거나 내용이 바뀌어 (재)정제가 필요함 */
    NEW,
    /** 정제 완료 */
    REFINED,
    /** 정제 실패 (error_message 참고) */
    FAILED
}
