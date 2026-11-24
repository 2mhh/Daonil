package com.sprint.daonil.domain.jobsync.service;

import com.sprint.daonil.domain.jobsync.dto.KeadCloseResult;
import com.sprint.daonil.domain.jobsync.dto.KeadIngestResult;
import com.sprint.daonil.domain.jobsync.dto.KeadPurgeResult;
import com.sprint.daonil.domain.jobsync.dto.KeadRefineResult;
import com.sprint.daonil.domain.jobsync.dto.KeadSyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 수집(Raw 적재) → 정제 → 마감 → Raw 정리를 순서대로 실행한다.
 * 순서가 중요하다: 마감이 먼저여야 "마감된 공고의 오래된 Raw"를 정리할 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeadJobSyncService {

    private final KeadJobIngestService ingestService;
    private final KeadJobRefineService refineService;
    private final KeadJobCloseService closeService;
    private final KeadRawPurgeService purgeService;

    public KeadSyncResult sync() {
        KeadIngestResult ingest = ingestService.ingest();
        KeadRefineResult refine = refineService.refineAll();
        KeadCloseResult close = closeService.closeDisappeared(ingest);
        KeadPurgeResult purge = purgeService.purgeStale(ingest);
        log.info("KEAD 동기화 완료: ingest={}, refine={}, close={}, purge={}", ingest, refine, close, purge);
        return new KeadSyncResult(ingest, refine, close, purge);
    }
}
