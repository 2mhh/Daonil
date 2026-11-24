package com.sprint.daonil.domain.jobsync.service;

import com.sprint.daonil.domain.jobsync.config.KeadSyncProperties;
import com.sprint.daonil.domain.jobsync.dto.KeadIngestResult;
import com.sprint.daonil.domain.jobsync.dto.KeadPurgeResult;
import com.sprint.daonil.domain.jobsync.repository.JobPostingRawRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeadRawPurgeServiceTest {

    private static final LocalDateTime RUN = LocalDateTime.of(2026, 11, 24, 3, 0, 0);

    @Mock JobPostingRawRepository rawRepository;

    private KeadSyncProperties properties;
    private KeadRawPurgeService service;

    @BeforeEach
    void setUp() {
        properties = new KeadSyncProperties();   // 기본 보관 기간 90일
        service = new KeadRawPurgeService(rawRepository, properties);
    }

    private static KeadIngestResult ingest(int totalCount, int fetched) {
        return new KeadIngestResult(RUN, totalCount, fetched, 0, 0, 0, 0, fetched, totalCount > 0 && totalCount == fetched);
    }

    @Test
    void 실행_시각에서_보관_기간을_뺀_시각을_기준으로_삭제한다() {
        LocalDateTime cutoff = RUN.minusDays(90);
        when(rawRepository.purgeStale(KeadJobIngestService.SOURCE, cutoff)).thenReturn(7);

        KeadPurgeResult result = service.purgeStale(ingest(239, 239));

        assertThat(result.skipped()).isFalse();
        assertThat(result.purged()).isEqualTo(7);
        assertThat(result.cutoff()).isEqualTo(LocalDateTime.of(2026, 8, 26, 3, 0, 0));
        verify(rawRepository).purgeStale(KeadJobIngestService.SOURCE, cutoff);
    }

    @Test
    void 보관_기간은_설정으로_바꿀_수_있다() {
        properties.setRawRetentionDays(30);
        when(rawRepository.purgeStale(KeadJobIngestService.SOURCE, RUN.minusDays(30))).thenReturn(0);

        assertThat(service.purgeStale(ingest(239, 239)).cutoff()).isEqualTo(RUN.minusDays(30));
    }

    @Test
    void 수집이_불완전하면_삭제하지_않는다() {
        KeadPurgeResult result = service.purgeStale(ingest(239, 100));

        assertThat(result.skipped()).isTrue();
        assertThat(result.reason()).contains("완전하지 않음");
        verifyNoInteractions(rawRepository);
    }

    @Test
    void API가_0건을_돌려주면_삭제하지_않는다() {
        assertThat(service.purgeStale(ingest(0, 0)).skipped()).isTrue();
        verifyNoInteractions(rawRepository);
    }

    @Test
    void 보관_기간이_0_이하면_정리를_끈다() {
        properties.setRawRetentionDays(0);

        KeadPurgeResult result = service.purgeStale(ingest(239, 239));

        assertThat(result.skipped()).isTrue();
        assertThat(result.reason()).contains("비활성");
        verifyNoInteractions(rawRepository);
    }
}
