package com.sprint.daonil.domain.jobsync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.daonil.domain.company.entity.Company;
import com.sprint.daonil.domain.company.repository.CompanyRepository;
import com.sprint.daonil.domain.jobposting.entity.JobPosting;
import com.sprint.daonil.domain.jobposting.repository.JobPostingRepository;
import com.sprint.daonil.domain.jobsync.client.KeadIdentity;
import com.sprint.daonil.domain.jobsync.client.KeadJobXmlParser;
import com.sprint.daonil.domain.jobsync.dto.KeadJobItem;
import com.sprint.daonil.domain.jobsync.dto.KeadRefineResult;
import com.sprint.daonil.domain.jobsync.entity.JobPostingRaw;
import com.sprint.daonil.domain.jobsync.entity.RawStatus;
import com.sprint.daonil.domain.jobsync.refine.KeadJobMapper;
import com.sprint.daonil.domain.jobsync.repository.JobPostingRawRepository;
import com.sprint.daonil.domain.member.entity.Member;
import com.sprint.daonil.domain.member.enumtype.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeadJobRefineServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 24, 3, 0);

    @Mock JobPostingRawRepository rawRepository;
    @Mock CompanyRepository companyRepository;
    @Mock JobPostingRepository jobPostingRepository;
    @Mock KeadSystemAccount systemAccount;
    @Mock TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private KeadJobRefineService service;
    private Member systemMember;

    @BeforeEach
    void setUp() {
        service = new KeadJobRefineService(rawRepository, companyRepository, jobPostingRepository,
                systemAccount, new KeadJobMapper(), objectMapper, transactionTemplate);
        systemMember = Member.builder().memberId(1L).loginId(KeadSystemAccount.LOGIN_ID).role(Role.COMPANY).build();

        // 트랜잭션 템플릿은 콜백을 그대로 실행하도록 흉내낸다
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv ->
                ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
        lenient().doAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private KeadJobItem sampleItem(int index) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/kead/job_list_env2_sample.xml")) {
            return new KeadJobXmlParser().parse(in.readAllBytes()).items().get(index);
        }
    }

    private JobPostingRaw rawOf(long id, KeadJobItem item) throws Exception {
        JobPostingRaw raw = JobPostingRaw.create(KeadJobIngestService.SOURCE, KeadIdentity.externalId(item),
                objectMapper.writeValueAsString(item.fields()), KeadIdentity.contentHash(item), NOW);
        // IDENTITY 키는 DB가 부여하므로 테스트에서는 리플렉션으로 지정한다
        var field = JobPostingRaw.class.getDeclaredField("rawId");
        field.setAccessible(true);
        field.set(raw, id);
        return raw;
    }

    @Test
    void 새_Raw는_회사와_공고를_만들고_REFINED로_표시한다() throws Exception {
        KeadJobItem item = sampleItem(0);
        JobPostingRaw raw = rawOf(1L, item);
        when(rawRepository.findById(1L)).thenReturn(Optional.of(raw));
        when(jobPostingRepository.findBySourceAndExternalId(KeadJobIngestService.SOURCE, raw.getExternalId()))
                .thenReturn(Optional.empty());
        when(companyRepository.findFirstByBusinessNumber(anyString())).thenReturn(Optional.empty());
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));
        when(systemAccount.getOrCreate()).thenReturn(systemMember);

        KeadJobRefineService.Outcome outcome = service.refineOne(1L);

        assertThat(outcome.created()).isTrue();
        assertThat(raw.getStatus()).isEqualTo(RawStatus.REFINED);

        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(companyCaptor.capture());
        Company company = companyCaptor.getValue();
        assertThat(company.getMember()).isSameAs(systemMember);          // 시스템 계정 소유
        assertThat(company.getBusinessNumber()).startsWith("EXT-").hasSizeLessThanOrEqualTo(30);
        assertThat(company.getCompanyName()).startsWith("쿠팡풀필먼트서비스");

        ArgumentCaptor<JobPosting> postingCaptor = ArgumentCaptor.forClass(JobPosting.class);
        verify(jobPostingRepository).save(postingCaptor.capture());
        JobPosting posting = postingCaptor.getValue();
        assertThat(posting.getSource()).isEqualTo(KeadJobIngestService.SOURCE);
        assertThat(posting.getExternalId()).isEqualTo(raw.getExternalId());
        assertThat(posting.getExternalContact()).isEqualTo("한국장애인고용공단 전남지사 1588-1519");
        assertThat(posting.isExternal()).isTrue();
        assertThat(posting.getIsClosed()).isFalse();
        assertThat(posting.getWorkRegion()).isEqualTo("서울 송파구");
        assertThat(posting.getCompany()).isSameAs(company);
    }

    @Test
    void 같은_사업장은_기존_회사를_재사용한다() throws Exception {
        KeadJobItem item = sampleItem(0);
        JobPostingRaw raw = rawOf(1L, item);
        Company existingCompany = Company.builder().companyId(7L).businessNumber("EXT-abc").build();
        when(rawRepository.findById(1L)).thenReturn(Optional.of(raw));
        when(jobPostingRepository.findBySourceAndExternalId(anyString(), anyString())).thenReturn(Optional.empty());
        when(companyRepository.findFirstByBusinessNumber(anyString())).thenReturn(Optional.of(existingCompany));

        service.refineOne(1L);

        verify(companyRepository, never()).save(any(Company.class));
        verify(systemAccount, never()).getOrCreate();
        ArgumentCaptor<JobPosting> captor = ArgumentCaptor.forClass(JobPosting.class);
        verify(jobPostingRepository).save(captor.capture());
        assertThat(captor.getValue().getCompany()).isSameAs(existingCompany);
    }

    @Test
    void 이미_있는_공고는_새로_만들지_않고_갱신한다() throws Exception {
        KeadJobItem item = sampleItem(1);
        JobPostingRaw raw = rawOf(2L, item);
        Company company = Company.builder().companyId(7L).build();
        JobPosting existing = JobPosting.builder().company(company).title("이전 제목").salary(1)
                .source(KeadJobIngestService.SOURCE).externalId(raw.getExternalId()).viewCount(42).isClosed(false).build();
        when(rawRepository.findById(2L)).thenReturn(Optional.of(raw));
        when(jobPostingRepository.findBySourceAndExternalId(KeadJobIngestService.SOURCE, raw.getExternalId()))
                .thenReturn(Optional.of(existing));
        when(companyRepository.findFirstByBusinessNumber(anyString())).thenReturn(Optional.of(company));

        KeadJobRefineService.Outcome outcome = service.refineOne(2L);

        assertThat(outcome.created()).isFalse();
        assertThat(existing.getTitle()).isEqualTo("거리 및 공공장소 청소원(환경 미화원)");
        assertThat(existing.getSalary()).isEqualTo(14_000_000);
        assertThat(existing.getViewCount()).isEqualTo(42);                   // 조회수는 유지
        assertThat(existing.getExternalContact()).isEqualTo("한국장애인고용공단 경기서부지사 1588-1519");
        assertThat(raw.getStatus()).isEqualTo(RawStatus.REFINED);
        verify(jobPostingRepository, never()).save(any(JobPosting.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 한_건이_실패해도_나머지는_계속_정제하고_실패_사유를_남긴다() throws Exception {
        KeadJobItem good = sampleItem(0);
        JobPostingRaw goodRaw = rawOf(1L, good);

        // 직무명이 없는 깨진 원본
        java.util.Map<String, String> brokenFields = new java.util.LinkedHashMap<>(sampleItem(1).fields());
        brokenFields.remove("jobNm");
        KeadJobItem broken = new KeadJobItem(brokenFields);
        JobPostingRaw brokenRaw = rawOf(2L, broken);

        when(rawRepository.findTop100BySourceAndStatusInAndRawIdGreaterThanOrderByRawIdAsc(
                eq(KeadJobIngestService.SOURCE), anyCollection(), anyLong()))
                .thenReturn(List.of(goodRaw, brokenRaw))
                .thenReturn(List.of());
        when(rawRepository.findById(1L)).thenReturn(Optional.of(goodRaw));
        when(rawRepository.findById(2L)).thenReturn(Optional.of(brokenRaw));
        when(jobPostingRepository.findBySourceAndExternalId(anyString(), anyString())).thenReturn(Optional.empty());
        when(companyRepository.findFirstByBusinessNumber(anyString())).thenReturn(Optional.empty());
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));
        when(systemAccount.getOrCreate()).thenReturn(systemMember);

        KeadRefineResult result = service.refineAll();

        assertThat(result.processed()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(goodRaw.getStatus()).isEqualTo(RawStatus.REFINED);
        assertThat(brokenRaw.getStatus()).isEqualTo(RawStatus.FAILED);
        assertThat(brokenRaw.getErrorMessage()).contains("jobNm");

        // 다음 조회는 마지막으로 처리한 id(2)부터 → OFFSET 없이 커서로 이어 읽는다
        ArgumentCaptor<Long> lastIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(rawRepository, org.mockito.Mockito.times(2))
                .findTop100BySourceAndStatusInAndRawIdGreaterThanOrderByRawIdAsc(
                        eq(KeadJobIngestService.SOURCE), any(Collection.class), lastIdCaptor.capture());
        assertThat(lastIdCaptor.getAllValues()).containsExactly(0L, 2L);
    }
}
