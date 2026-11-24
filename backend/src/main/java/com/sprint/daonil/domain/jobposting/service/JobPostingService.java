package com.sprint.daonil.domain.jobposting.service;

import com.sprint.daonil.domain.company.entity.Company;
import com.sprint.daonil.domain.company.repository.CompanyRepository;
import com.sprint.daonil.domain.jobposting.dto.JobPostingRequestDto;
import com.sprint.daonil.domain.jobposting.dto.JobPostingResponseDto;
import com.sprint.daonil.domain.jobposting.entity.JobPosting;
import com.sprint.daonil.domain.jobposting.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final CompanyRepository companyRepository;
    private final com.sprint.daonil.domain.apply.repository.ApplicationRepository applicationRepository;
    private final ViewCountBuffer viewCountBuffer;

    /** true 면 조회수를 모아서 반영한다 (환경변수 APP_VIEW_COUNT_BUFFERED). 기본은 기존 동작. */
    @org.springframework.beans.factory.annotation.Value("${app.view-count.buffered:false}")
    private boolean bufferedViewCount;

    // 채용공고 목록 조회 (다중 필터 + 페이징)
    @Transactional(readOnly = true)
    public Page<JobPostingResponseDto> getJobPostings(String keyword, String mainCategory, String subCategory,
                                                      String workRegion,
                                                      String envBothHands, String envEyesight, 
                                                      String envHandWork, String envLiftPower, 
                                                      String envLstnTalk, String envStndWalk,
                                                      String sortBy, Pageable pageable) {
        
        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.by("createdAt").descending();
        
        if ("views".equals(sortBy)) {
            sort = org.springframework.data.domain.Sort.by("viewCount").descending();
        } else if ("deadline".equals(sortBy)) {
            sort = org.springframework.data.domain.Sort.by("applicationEndDate").ascending();
        }
        
        org.springframework.data.domain.Pageable sortedPageable = org.springframework.data.domain.PageRequest.of(
            pageable.getPageNumber(), pageable.getPageSize(), sort);

        Page<JobPosting> result;
        if ("applicants".equals(sortBy)) {
            result = jobPostingRepository.findByFiltersOrderByApplicants(keyword, mainCategory, subCategory, workRegion, 
                    envBothHands, envEyesight, envHandWork, envLiftPower, envLstnTalk, envStndWalk,
                    false, pageable);
        } else {
            result = jobPostingRepository.findByFilters(keyword, mainCategory, subCategory, workRegion, 
                    envBothHands, envEyesight, envHandWork, envLiftPower, envLstnTalk, envStndWalk,
                    false, sortedPageable);
        }

        return toDtoPage(result);
    }

    /** 페이지의 공고들의 지원자 수를 쿼리 1번으로 구해 DTO 로 변환한다 (공고마다 따로 세던 N+1 제거). */
    private Page<JobPostingResponseDto> toDtoPage(Page<JobPosting> page) {
        java.util.Map<Long, Integer> counts = applicationRepository.countsByJobPostingIds(
                page.getContent().stream().map(JobPosting::getJobPostingId).toList());
        return page.map(job -> JobPostingResponseDto.fromEntity(job, counts.getOrDefault(job.getJobPostingId(), 0)));
    }

    // 특정 회사 채용공고 목록 조회
    @Transactional(readOnly = true)
    public Page<JobPostingResponseDto> getJobPostingsByCompanyId(String loginId, Pageable pageable) {
        return toDtoPage(jobPostingRepository.findByCompany_Member_LoginId(loginId, pageable));
    }

    // 채용공고 상세 조회 (조회수 증가 포함)
    public JobPostingResponseDto getJobPostingDetail(Long jobPostingId) {
        // 1. 조회수 증가. 기본: 조회마다 DB 에서 직접 UPDATE (동시성 문제 방지, 같은 행 락 경합 있음)
        //    app.view-count.buffered=true: 메모리에 모았다가 주기적으로 한 번에 반영 (락 경합 제거, docs/LOAD_TEST.md)
        if (bufferedViewCount) {
            viewCountBuffer.increment(jobPostingId);
        } else {
            jobPostingRepository.incrementViewCount(jobPostingId);
        }

        // 2. 엔티티 조회
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new IllegalArgumentException("채용공고를 찾을 수 없습니다."));

        int count = applicationRepository.countByJobPosting_JobPostingId(jobPostingId);

        JobPostingResponseDto dto = JobPostingResponseDto.fromEntity(jobPosting, count);
        if (bufferedViewCount) {
            // DB 값 + 아직 반영 전인 값(이번 조회 포함)
            int stored = jobPosting.getViewCount() == null ? 0 : jobPosting.getViewCount();
            dto.setViewCount((int) (stored + viewCountBuffer.pendingOf(jobPostingId)));
        }
        return dto;
    }

    // 채용공고 등록 (Builder 패턴 적용)
    public JobPostingResponseDto createJobPosting(String loginId, JobPostingRequestDto requestDto) {
        Company company = companyRepository.findByMember_LoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("기업 정보를 찾을 수 없습니다."));

        JobPosting jobPosting = requestDto.toEntity(company);

        JobPosting savedJobPosting = jobPostingRepository.save(jobPosting);

        return JobPostingResponseDto.fromEntity(savedJobPosting);
    }

    // 채용공고 수정 (Dirty Checking 및 권한 검증)
    public JobPostingResponseDto updateJobPosting(String loginId, Long jobPostingId, JobPostingRequestDto requestDto) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new IllegalArgumentException("채용공고를 찾을 수 없습니다."));

        validateCompanyAccess(jobPosting, loginId);

        // 엔티티의 비즈니스 메서드 호출. 트랜잭션 종료 시 알아서 UPDATE 쿼리 발생
        jobPosting.update(requestDto);

        return JobPostingResponseDto.fromEntity(jobPosting);
    }

    // 채용공고 마감 (Dirty Checking 및 권한 검증)
    public JobPostingResponseDto closeJobPosting(String loginId, Long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new IllegalArgumentException("채용공고를 찾을 수 없습니다."));

        validateCompanyAccess(jobPosting, loginId);

        // 엔티티 상태 변경
        jobPosting.close();

        return JobPostingResponseDto.fromEntity(jobPosting);
    }

    // 작성 기업 권한 검증 공통 로직
    private void validateCompanyAccess(JobPosting jobPosting, String loginId) {
        // 외부 API 출처 공고는 시스템 계정 소유이며 수정·마감할 수 없다 (이중 방어: 계정은 로그인 불가)
        if (jobPosting.isExternal()) {
            throw new IllegalArgumentException("외부 출처 공고는 수정하거나 마감할 수 없습니다.");
        }
        if (!jobPosting.getCompany().getMember().getLoginId().equals(loginId)) {
            throw new IllegalArgumentException("해당 채용공고에 대한 권한이 없습니다.");
        }
    }
}