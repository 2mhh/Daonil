package com.sprint.daonil.domain.jobsync.service;

import com.sprint.daonil.domain.jobsync.config.KeadSyncProperties;
import com.sprint.daonil.domain.jobsync.dto.KeadCloseResult;
import com.sprint.daonil.domain.jobsync.dto.KeadIngestResult;
import com.sprint.daonil.domain.jobsync.repository.KeadJobPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * API 목록에서 사라진 외부 공고를 마감한다.
 *
 * 이 API는 "현재 진행 중인 공고"의 스냅샷이라 끝난 공고는 목록에서 빠진다.
 * 따라서 사라진 공고를 닫지 않으면 끝난 공고가 계속 노출된다.
 *
 * 위험: API 오류로 빈·부분 응답이 오면 멀쩡한 공고가 전부 "사라진 것"으로 보인다. 그래서
 *  1) 수집이 완전할 때만(수집 건수 == totalCount) 마감하고,
 *  2) 한 번에 너무 많이 사라지면(안전장치 한도 초과) 마감하지 않고 경고만 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeadJobCloseService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final KeadJobPostingRepository postingRepository;
    private final KeadSyncProperties properties;

    @Transactional
    public KeadCloseResult closeDisappeared(KeadIngestResult ingest) {
        if (!ingest.complete()) {
            String reason = "수집이 완전하지 않음 (fetched=" + ingest.fetched() + ", totalCount=" + ingest.totalCount() + ")";
            log.warn("KEAD 마감 건너뜀: {}", reason);
            return KeadCloseResult.skipped(reason, 0, 0);
        }

        String source = KeadJobIngestService.SOURCE;
        LocalDateTime runStartedAt = ingest.startedAt();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now(SEOUL);

        long openBefore = postingRepository.countOpen(source);
        long disappeared = postingRepository.countDisappeared(source, runStartedAt);

        // 다시 나타난 공고는 안전장치와 무관하게 되살린다 (이번 목록에 있다는 것이 확실한 사실)
        int reopened = postingRepository.reopenReappeared(source, runStartedAt, today, now);

        long limit = properties.closeLimit(openBefore);
        if (disappeared > limit) {
            String reason = "사라진 공고가 한도를 넘음 (disappeared=" + disappeared + ", limit=" + limit
                    + ", open=" + openBefore + ") — API 장애·부분 응답 가능성으로 마감하지 않음";
            log.warn("KEAD 마감 건너뜀: {}", reason);
            return new KeadCloseResult(0, reopened, true, reason, openBefore, disappeared);
        }

        int closed = disappeared == 0 ? 0 : postingRepository.closeDisappeared(source, runStartedAt, now);
        log.info("KEAD 마감: closed={}, reopened={}, openBefore={}", closed, reopened, openBefore);
        return new KeadCloseResult(closed, reopened, false, null, openBefore, disappeared);
    }
}
