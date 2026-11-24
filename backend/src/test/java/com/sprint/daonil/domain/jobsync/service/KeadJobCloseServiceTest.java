package com.sprint.daonil.domain.jobsync.service;

import com.sprint.daonil.domain.jobsync.config.KeadSyncProperties;
import com.sprint.daonil.domain.jobsync.dto.KeadCloseResult;
import com.sprint.daonil.domain.jobsync.dto.KeadIngestResult;
import com.sprint.daonil.domain.jobsync.repository.KeadJobPostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeadJobCloseServiceTest {

    private static final LocalDateTime RUN = LocalDateTime.of(2026, 11, 24, 3, 0, 0);
    private static final String SOURCE = KeadJobIngestService.SOURCE;

    @Mock KeadJobPostingRepository repository;

    private KeadSyncProperties properties;
    private KeadJobCloseService service;

    @BeforeEach
    void setUp() {
        properties = new KeadSyncProperties();   // 기본: 비율 0.3, 최소 10건
        service = new KeadJobCloseService(repository, properties);
    }

    private static KeadIngestResult ingest(int totalCount, int fetched) {
        return new KeadIngestResult(RUN, totalCount, fetched, 0, 0, 0, 0, fetched, totalCount > 0 && totalCount == fetched);
    }

    @Test
    void 사라진_공고를_마감하고_다시_나타난_공고는_되살린다() {
        when(repository.countOpen(SOURCE)).thenReturn(100L);
        when(repository.countDisappeared(SOURCE, RUN)).thenReturn(3L);
        when(repository.reopenReappeared(eq(SOURCE), eq(RUN), any(LocalDate.class), any(LocalDateTime.class))).thenReturn(2);
        when(repository.closeDisappeared(eq(SOURCE), eq(RUN), any(LocalDateTime.class))).thenReturn(3);

        KeadCloseResult result = service.closeDisappeared(ingest(239, 239));

        assertThat(result.skipped()).isFalse();
        assertThat(result.closed()).isEqualTo(3);
        assertThat(result.reopened()).isEqualTo(2);
        assertThat(result.openBefore()).isEqualTo(100);
        assertThat(result.disappeared()).isEqualTo(3);
    }

    @Test
    void 사라진_공고가_없으면_마감_쿼리를_실행하지_않는다() {
        when(repository.countOpen(SOURCE)).thenReturn(100L);
        when(repository.countDisappeared(SOURCE, RUN)).thenReturn(0L);
        when(repository.reopenReappeared(eq(SOURCE), eq(RUN), any(LocalDate.class), any(LocalDateTime.class))).thenReturn(0);

        KeadCloseResult result = service.closeDisappeared(ingest(239, 239));

        assertThat(result.closed()).isZero();
        verify(repository, never()).closeDisappeared(any(), any(), any());
    }

    @Test
    void 수집이_불완전하면_아무것도_하지_않는다() {
        KeadCloseResult result = service.closeDisappeared(ingest(239, 120));   // 120/239만 받음

        assertThat(result.skipped()).isTrue();
        assertThat(result.reason()).contains("완전하지 않음");
        verifyNoInteractions(repository);
    }

    @Test
    void 빈_응답이면_아무것도_하지_않는다() {
        KeadCloseResult result = service.closeDisappeared(ingest(0, 0));   // API가 0건을 돌려준 경우

        assertThat(result.skipped()).isTrue();
        verifyNoInteractions(repository);
    }

    @Test
    void 한꺼번에_너무_많이_사라지면_마감하지_않는다() {
        when(repository.countOpen(SOURCE)).thenReturn(100L);          // 한도 = max(10, ceil(30.0)) = 30
        when(repository.countDisappeared(SOURCE, RUN)).thenReturn(31L);
        when(repository.reopenReappeared(eq(SOURCE), eq(RUN), any(LocalDate.class), any(LocalDateTime.class))).thenReturn(0);

        KeadCloseResult result = service.closeDisappeared(ingest(239, 239));

        assertThat(result.skipped()).isTrue();
        assertThat(result.reason()).contains("한도").contains("disappeared=31").contains("limit=30");
        verify(repository, never()).closeDisappeared(any(), any(), any());
    }

    @Test
    void 한도_이내면_마감한다() {
        when(repository.countOpen(SOURCE)).thenReturn(100L);
        when(repository.countDisappeared(SOURCE, RUN)).thenReturn(30L);   // 한도와 같으면 허용
        when(repository.reopenReappeared(eq(SOURCE), eq(RUN), any(LocalDate.class), any(LocalDateTime.class))).thenReturn(0);
        when(repository.closeDisappeared(eq(SOURCE), eq(RUN), any(LocalDateTime.class))).thenReturn(30);

        assertThat(service.closeDisappeared(ingest(239, 239)).closed()).isEqualTo(30);
    }

    @Test
    void 소규모에서는_최소_허용_건수가_적용된다() {
        when(repository.countOpen(SOURCE)).thenReturn(5L);            // 5 × 0.3 = 2 이지만 최소 10건은 허용
        when(repository.countDisappeared(SOURCE, RUN)).thenReturn(4L);
        when(repository.reopenReappeared(eq(SOURCE), eq(RUN), any(LocalDate.class), any(LocalDateTime.class))).thenReturn(0);
        when(repository.closeDisappeared(eq(SOURCE), eq(RUN), any(LocalDateTime.class))).thenReturn(4);

        assertThat(service.closeDisappeared(ingest(239, 239)).skipped()).isFalse();
    }

    @Test
    void 마감_한도_계산() {
        assertThat(properties.closeLimit(100)).isEqualTo(30);
        assertThat(properties.closeLimit(234)).isEqualTo(71);   // ceil(70.2)
        assertThat(properties.closeLimit(5)).isEqualTo(10);     // 최소 건수
        assertThat(properties.closeLimit(0)).isEqualTo(10);
    }
}
