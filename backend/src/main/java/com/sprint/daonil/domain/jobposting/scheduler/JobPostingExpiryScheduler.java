package com.sprint.daonil.domain.jobposting.scheduler;

import com.sprint.daonil.domain.jobposting.service.JobPostingExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 접수 기간이 지난 공고를 매일 마감한다. 기본은 꺼져 있고 APP_JOB_EXPIRY_ENABLED=true 로 켠다.
 * 실행 시각은 APP_JOB_EXPIRY_CRON (기본 매일 00:10, 서울 시간).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.job-expiry.enabled", havingValue = "true")
public class JobPostingExpiryScheduler {

    private final JobPostingExpiryService expiryService;

    @Scheduled(cron = "${app.job-expiry.cron:0 10 0 * * *}", zone = "Asia/Seoul")
    public void run() {
        try {
            expiryService.closeExpired();
        } catch (Exception e) {
            log.error("공고 만료 마감 실패: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
