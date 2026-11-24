package com.sprint.daonil.domain.jobposting.service;

import com.sprint.daonil.domain.jobposting.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/** 접수 마감일이 지난 공고를 마감 처리한다 (내부·외부 공고 공통). */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobPostingExpiryService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final JobPostingRepository jobPostingRepository;

    @Transactional
    public int closeExpired() {
        return closeExpired(LocalDate.now(SEOUL));
    }

    @Transactional
    public int closeExpired(LocalDate today) {
        int closed = jobPostingRepository.closeExpiredJobPostings(today);
        log.info("접수 기간이 지난 공고 마감: {}건 (기준일 {})", closed, today);
        return closed;
    }
}
