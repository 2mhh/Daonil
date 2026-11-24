package com.sprint.daonil.domain.jobsync.dto;

/**
 * 마감 처리 결과.
 *
 * @param closed      이번에 마감한 공고 (API 목록에서 사라진 공고)
 * @param reopened    다시 나타나 되살린 공고
 * @param skipped     마감 처리를 건너뛰었는가 (불완전 수집 또는 안전장치)
 * @param reason      건너뛴 이유 (건너뛰지 않았으면 null)
 * @param openBefore  처리 전 열려 있던 외부 공고 수
 * @param disappeared 사라진 것으로 판단된 공고 수
 */
public record KeadCloseResult(int closed, int reopened, boolean skipped, String reason,
                              long openBefore, long disappeared) {

    public static KeadCloseResult skipped(String reason, long openBefore, long disappeared) {
        return new KeadCloseResult(0, 0, true, reason, openBefore, disappeared);
    }
}
