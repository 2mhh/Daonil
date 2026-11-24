package com.sprint.daonil.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 를 활성화한다. 실제 작업은 각 스케줄러 빈이 프로퍼티로 켜질 때만 등록되므로
 * (kead.sync.enabled, app.job-expiry.enabled) 이 설정만으로 무언가 실행되지는 않는다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
