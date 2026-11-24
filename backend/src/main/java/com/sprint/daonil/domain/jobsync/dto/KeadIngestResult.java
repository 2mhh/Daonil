package com.sprint.daonil.domain.jobsync.dto;

import java.time.LocalDateTime;

/**
 * Raw 적재 결과 요약 (검증·로그용).
 *
 * @param startedAt        이번 실행의 기준 시각 (last_seen_at 비교 기준)
 * @param totalCount       API가 알려준 전체 건수
 * @param fetched          실제로 받은 건수
 * @param duplicatesMerged 식별키와 내용이 모두 같아 하나로 합친 건수
 * @param conflicts        식별키는 같지만 내용이 달라 첫 항목만 남긴 건수
 * @param inserted         새로 저장
 * @param updated          내용이 바뀌어 갱신 (재정제 대상으로 되돌림)
 * @param unchanged        변경 없음 (확인 시각만 갱신)
 * @param complete         fetched == totalCount 인 완전한 수집인가
 */
public record KeadIngestResult(
        LocalDateTime startedAt,
        int totalCount,
        int fetched,
        int duplicatesMerged,
        int conflicts,
        int inserted,
        int updated,
        int unchanged,
        boolean complete) {

    /** 저장 대상이 된 고유 공고 수 */
    public int unique() {
        return inserted + updated + unchanged;
    }
}
