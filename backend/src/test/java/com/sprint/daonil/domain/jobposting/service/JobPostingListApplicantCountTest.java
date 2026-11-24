package com.sprint.daonil.domain.jobposting.service;

import com.sprint.daonil.domain.apply.repository.ApplicationRepository;
import com.sprint.daonil.domain.company.entity.Company;
import com.sprint.daonil.domain.company.repository.CompanyRepository;
import com.sprint.daonil.domain.jobposting.dto.JobPostingResponseDto;
import com.sprint.daonil.domain.jobposting.entity.JobPosting;
import com.sprint.daonil.domain.jobposting.repository.JobPostingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 목록 조회가 지원자 수를 공고마다 따로 세지 않고 한 번에 집계하는지 확인한다 (N+1 제거). */
class JobPostingListApplicantCountTest {

    private final JobPostingRepository jobPostingRepository = mock(JobPostingRepository.class);
    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final JobPostingService service = new JobPostingService(
            jobPostingRepository, mock(CompanyRepository.class), applicationRepository, mock(ViewCountBuffer.class));

    private JobPosting job(long id) {
        Company company = Company.builder().companyId(1L).companyName("회사").build();
        return JobPosting.builder().jobPostingId(id).company(company).title("공고" + id).isClosed(false).viewCount(0).build();
    }

    @Test
    void 목록_조회는_지원자_수를_한_번에_집계한다() {
        Pageable pageable = PageRequest.of(0, 12);
        Page<JobPosting> page = new PageImpl<>(List.of(job(1), job(2), job(3)), pageable, 3);
        when(jobPostingRepository.findByFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(false), any())).thenReturn(page);
        when(applicationRepository.countsByJobPostingIds(List.of(1L, 2L, 3L))).thenReturn(Map.of(1L, 4, 3L, 1));

        Page<JobPostingResponseDto> result = service.getJobPostings(
                null, null, null, null, null, null, null, null, null, null, "latest", pageable);

        assertThat(result.getContent()).extracting(JobPostingResponseDto::getApplicantCount).containsExactly(4, 0, 1);
        verify(applicationRepository, times(1)).countsByJobPostingIds(any());
        verify(applicationRepository, never()).countByJobPosting_JobPostingId(anyLong());
    }

    @Test
    void 결과가_없으면_빈_목록을_돌려준다() {
        Pageable pageable = PageRequest.of(0, 12);
        when(jobPostingRepository.findByFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(false), any())).thenReturn(new PageImpl<>(List.of(), pageable, 0));
        when(applicationRepository.countsByJobPostingIds(List.of())).thenReturn(Map.of());

        Page<JobPostingResponseDto> result = service.getJobPostings(
                null, null, null, null, null, null, null, null, null, null, "latest", pageable);

        assertThat(result.getContent()).isEmpty();
    }
}
