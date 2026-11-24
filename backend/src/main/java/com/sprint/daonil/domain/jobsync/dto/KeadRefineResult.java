package com.sprint.daonil.domain.jobsync.dto;

/**
 * 정제 결과 요약.
 *
 * @param processed    이번 실행에서 정제를 시도한 Raw 건수
 * @param created      새로 만든 공고
 * @param updated      기존 공고 갱신
 * @param failed       정제 실패 (raw.status = FAILED, 사유 기록)
 * @param withWarnings 변환은 됐지만 값 보정/누락 경고가 있는 건수
 */
public record KeadRefineResult(int processed, int created, int updated, int failed, int withWarnings) {
}
