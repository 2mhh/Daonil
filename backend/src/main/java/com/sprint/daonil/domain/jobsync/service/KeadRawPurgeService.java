package com.sprint.daonil.domain.jobsync.service;

import com.sprint.daonil.domain.jobsync.config.KeadSyncProperties;
import com.sprint.daonil.domain.jobsync.dto.KeadIngestResult;
import com.sprint.daonil.domain.jobsync.dto.KeadPurgeResult;
import com.sprint.daonil.domain.jobsync.repository.JobPostingRawRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 오래된 Raw 정리.
 *
 * Raw 는 계속 쌓이므로, 보관 기간(기본 90일) 동안 API 목록에서 확인되지 않았고
 * 연결된 서비스 공고가 이미 마감된 행만 삭제한다.
 *  - 서비스 공고(job_posting)는 지우지 않는다: 북마크·지원 이력이 참조한다.
 *  - 열려 있는 공고의 Raw 는 지우지 않는다: 마감 안전장치로 닫히지 않은 공고일 수 있다.
 *  - 이번 수집이 완전할 때만 실행한다: "확인되지 않음"이 API 장애 때문이 아니라 정말 목록에서 빠진 것임이 확실해야 한다.
 *
 * 삭제하면 재정제·변경 이력 확인 근거가 사라지므로 보관 기간은 충분히 길게 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeadRawPurgeService {

    private final JobPostingRawRepository rawRepository;
    private final KeadSyncProperties properties;

    @Transactional
    public KeadPurgeResult purgeStale(KeadIngestResult ingest) {
        int retentionDays = properties.getRawRetentionDays();
        if (retentionDays <= 0) {
            return KeadPurgeResult.skipped("Raw 정리 비활성 (rawRetentionDays=" + retentionDays + ")");
        }
        if (!ingest.complete()) {
            String reason = "수집이 완전하지 않음 (fetched=" + ingest.fetched() + ", totalCount=" + ingest.totalCount() + ")";
            log.warn("KEAD Raw 정리 건너뜀: {}", reason);
            return KeadPurgeResult.skipped(reason);
        }

        // 기준은 이번 실행 시각: 이번에 확인된 행(last_seen_at = 실행 시각)은 절대 대상이 아니다
        LocalDateTime cutoff = ingest.startedAt().minusDays(retentionDays);
        int purged = rawRepository.purgeStale(KeadJobIngestService.SOURCE, cutoff);
        log.info("KEAD Raw 정리: purged={}, cutoff={} (보관 {}일)", purged, cutoff, retentionDays);
        return new KeadPurgeResult(purged, false, null, cutoff);
    }
}
