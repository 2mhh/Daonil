package com.sprint.daonil.domain.jobsync.service;

import com.sprint.daonil.domain.jobsync.dto.KeadCloseResult;
import com.sprint.daonil.domain.jobsync.dto.KeadIngestResult;
import com.sprint.daonil.domain.jobsync.dto.KeadPurgeResult;
import com.sprint.daonil.domain.jobsync.dto.KeadRefineResult;
import com.sprint.daonil.domain.jobsync.dto.KeadSyncResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeadJobSyncServiceTest {

    @Mock KeadJobIngestService ingestService;
    @Mock KeadJobRefineService refineService;
    @Mock KeadJobCloseService closeService;
    @Mock KeadRawPurgeService purgeService;

    @Test
    void 수집_정제_마감_정리_순서로_실행하고_수집_결과를_뒤_단계에_넘긴다() {
        KeadIngestResult ingest = new KeadIngestResult(LocalDateTime.of(2026, 11, 24, 3, 0), 239, 239, 1, 0, 0, 0, 238, true);
        KeadRefineResult refine = new KeadRefineResult(0, 0, 0, 0, 0);
        KeadCloseResult close = new KeadCloseResult(0, 0, false, null, 234, 0);
        KeadPurgeResult purge = new KeadPurgeResult(0, false, null, LocalDateTime.of(2026, 8, 26, 3, 0));
        when(ingestService.ingest()).thenReturn(ingest);
        when(refineService.refineAll()).thenReturn(refine);
        when(closeService.closeDisappeared(ingest)).thenReturn(close);
        when(purgeService.purgeStale(ingest)).thenReturn(purge);

        KeadSyncResult result = new KeadJobSyncService(ingestService, refineService, closeService, purgeService).sync();

        // 마감이 먼저여야 "마감된 공고의 오래된 Raw"를 정리할 수 있다
        InOrder order = inOrder(ingestService, refineService, closeService, purgeService);
        order.verify(ingestService).ingest();
        order.verify(refineService).refineAll();
        order.verify(closeService).closeDisappeared(ingest);
        order.verify(purgeService).purgeStale(ingest);
        assertThat(result.ingest()).isSameAs(ingest);
        assertThat(result.refine()).isSameAs(refine);
        assertThat(result.close()).isSameAs(close);
        assertThat(result.purge()).isSameAs(purge);
    }
}
