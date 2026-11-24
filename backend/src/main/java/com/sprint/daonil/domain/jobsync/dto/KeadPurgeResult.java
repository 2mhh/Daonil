package com.sprint.daonil.domain.jobsync.dto;

import java.time.LocalDateTime;

/**
 * Raw 정리 결과.
 *
 * @param purged  삭제한 Raw 행 수
 * @param skipped 정리를 건너뛰었는가
 * @param reason  건너뛴 이유 (건너뛰지 않았으면 null)
 * @param cutoff  이 시각 이전에 마지막으로 확인된 Raw 가 삭제 대상이었다 (건너뛰면 null)
 */
public record KeadPurgeResult(int purged, boolean skipped, String reason, LocalDateTime cutoff) {

    public static KeadPurgeResult skipped(String reason) {
        return new KeadPurgeResult(0, true, reason, null);
    }
}
