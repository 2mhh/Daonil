package com.sprint.daonil.domain.jobsync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.daonil.domain.jobsync.client.KeadIdentity;
import com.sprint.daonil.domain.jobsync.client.KeadJobApiClient;
import com.sprint.daonil.domain.jobsync.dto.KeadFetchResult;
import com.sprint.daonil.domain.jobsync.dto.KeadIngestResult;
import com.sprint.daonil.domain.jobsync.dto.KeadJobItem;
import com.sprint.daonil.domain.jobsync.entity.JobPostingRaw;
import com.sprint.daonil.domain.jobsync.entity.RawStatus;
import com.sprint.daonil.domain.jobsync.repository.JobPostingRawRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeadJobIngestServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 24, 3, 0);
    private static final LocalDateTime BEFORE = LocalDateTime.of(2026, 11, 23, 3, 0);

    @Mock KeadJobApiClient client;
    @Mock JobPostingRawRepository repository;
    @Mock TransactionTemplate transactionTemplate;

    private KeadJobIngestService service;

    @BeforeEach
    void setUp() {
        service = new KeadJobIngestService(client, repository, new ObjectMapper(), transactionTemplate);
    }

    private static KeadJobItem item(String company, String job, String salary, String rno) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("busplaName", company);
        fields.put("jobNm", job);
        fields.put("termDate", "2026-11-01~2026-12-01");
        fields.put("compAddr", "서울특별시 종로구");
        fields.put("salary", salary);
        fields.put("rno", rno);
        return new KeadJobItem(fields);
    }

    private static JobPostingRaw existingRow(KeadJobItem item, RawStatus status) {
        JobPostingRaw row = JobPostingRaw.create(KeadJobIngestService.SOURCE, KeadIdentity.externalId(item),
                "{}", KeadIdentity.contentHash(item), BEFORE);
        if (status == RawStatus.REFINED) {
            row.markRefined();
        }
        return row;
    }

    @SuppressWarnings("unchecked")
    private List<JobPostingRaw> capturedInserts() {
        ArgumentCaptor<Iterable<JobPostingRaw>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        List<JobPostingRaw> rows = new java.util.ArrayList<>();
        captor.getValue().forEach(rows::add);
        return rows;
    }

    @Test
    void 새_공고는_NEW_상태로_저장한다() {
        KeadJobItem a = item("A사", "청소원", "1,000", "1");
        KeadJobItem b = item("B사", "사무 보조원", "2,000", "2");
        when(repository.findBySourceAndExternalIdIn(eq(KeadJobIngestService.SOURCE), anyCollection())).thenReturn(List.of());

        KeadIngestResult result = service.persist(new KeadFetchResult(2, List.of(a, b)), NOW);

        assertThat(result.inserted()).isEqualTo(2);
        assertThat(result.updated()).isZero();
        assertThat(result.unchanged()).isZero();
        assertThat(result.complete()).isTrue();
        List<JobPostingRaw> saved = capturedInserts();
        assertThat(saved).hasSize(2);
        assertThat(saved).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(RawStatus.NEW);
            assertThat(row.getSource()).isEqualTo(KeadJobIngestService.SOURCE);
            assertThat(row.getFirstSeenAt()).isEqualTo(NOW);
            assertThat(row.getPayload()).contains("busplaName");
        });
    }

    @Test
    void 내용이_같은_공고는_확인_시각만_갱신한다() {
        KeadJobItem a = item("A사", "청소원", "1,000", "1");
        JobPostingRaw row = existingRow(a, RawStatus.NEW);
        when(repository.findBySourceAndExternalIdIn(eq(KeadJobIngestService.SOURCE), anyCollection())).thenReturn(List.of(row));

        // 응답 안의 순번(rno)이 바뀌어도 변경으로 보지 않는다
        KeadIngestResult result = service.persist(new KeadFetchResult(1, List.of(item("A사", "청소원", "1,000", "99"))), NOW);

        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.inserted()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(row.getLastSeenAt()).isEqualTo(NOW);
        assertThat(row.getFirstSeenAt()).isEqualTo(BEFORE);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void 내용이_바뀐_공고는_갱신하고_NEW로_되돌린다() {
        KeadJobItem before = item("A사", "청소원", "1,000", "1");
        JobPostingRaw row = existingRow(before, RawStatus.REFINED);
        assertThat(row.getStatus()).isEqualTo(RawStatus.REFINED);   // 사전 조건: 이미 정제된 행
        when(repository.findBySourceAndExternalIdIn(eq(KeadJobIngestService.SOURCE), anyCollection())).thenReturn(List.of(row));

        KeadJobItem after = item("A사", "청소원", "2,000", "1");   // 급여 변경 (식별키는 동일)
        KeadIngestResult result = service.persist(new KeadFetchResult(1, List.of(after)), NOW);

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.inserted()).isZero();
        assertThat(row.getStatus()).isEqualTo(RawStatus.NEW);
        assertThat(row.getContentHash()).isEqualTo(KeadIdentity.contentHash(after));
        assertThat(row.getPayload()).contains("2,000");
        assertThat(row.getLastSeenAt()).isEqualTo(NOW);
    }

    @Test
    void 같은_공고가_두번_내려오면_하나로_합친다() {
        KeadJobItem a = item("A사", "청소원", "1,000", "104");
        KeadJobItem dup = item("A사", "청소원", "1,000", "105");   // rno만 다름 (실제 데이터의 rno 104·105 사례)
        when(repository.findBySourceAndExternalIdIn(eq(KeadJobIngestService.SOURCE), anyCollection())).thenReturn(List.of());

        KeadIngestResult result = service.persist(new KeadFetchResult(2, List.of(a, dup)), NOW);

        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.duplicatesMerged()).isEqualTo(1);
        assertThat(result.inserted()).isEqualTo(1);
        assertThat(capturedInserts()).hasSize(1);
    }

    @Test
    void 식별키는_같지만_내용이_다르면_충돌로_기록하고_첫_항목만_남긴다() {
        KeadJobItem first = item("A사", "청소원", "1,000", "1");
        KeadJobItem conflicting = item("A사", "청소원", "9,999", "2");
        when(repository.findBySourceAndExternalIdIn(eq(KeadJobIngestService.SOURCE), anyCollection())).thenReturn(List.of());

        KeadIngestResult result = service.persist(new KeadFetchResult(2, List.of(first, conflicting)), NOW);

        assertThat(result.conflicts()).isEqualTo(1);
        assertThat(result.duplicatesMerged()).isZero();
        List<JobPostingRaw> saved = capturedInserts();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getPayload()).contains("1,000").doesNotContain("9,999");
    }

    @Test
    void 수집_건수가_totalCount보다_적으면_complete가_false다() {
        KeadJobItem a = item("A사", "청소원", "1,000", "1");
        when(repository.findBySourceAndExternalIdIn(eq(KeadJobIngestService.SOURCE), anyCollection())).thenReturn(List.of());

        KeadIngestResult result = service.persist(new KeadFetchResult(5, List.of(a)), NOW);

        assertThat(result.complete()).isFalse();
        assertThat(result.totalCount()).isEqualTo(5);
        assertThat(result.fetched()).isEqualTo(1);
    }

    @Test
    void 빈_결과면_저장소를_건드리지_않는다() {
        KeadIngestResult result = service.persist(new KeadFetchResult(0, List.of()), NOW);

        assertThat(result.fetched()).isZero();
        assertThat(result.unique()).isZero();
        assertThat(result.complete()).isFalse();
        verify(repository, never()).saveAll(any());
        verify(repository, never()).findBySourceAndExternalIdIn(any(), anyCollection());
    }
}
