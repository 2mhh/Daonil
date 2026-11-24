package com.sprint.daonil.domain.jobsync.service;

import com.sprint.daonil.domain.jobsync.client.KeadApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 앱 시작 시 1회 수집+정제하는 수동 확인용 러너. 기본은 꺼져 있다.
 * 켜기: 환경변수 KEAD_SYNC_RUN_ON_STARTUP=true (또는 --kead.sync.run-on-startup=true)
 * (정식 실행은 이후 스케줄러가 맡는다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kead.sync.run-on-startup", havingValue = "true")
public class KeadJobSyncRunner implements ApplicationRunner {

    private final KeadJobSyncService syncService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            syncService.sync();
        } catch (KeadApiException e) {
            log.error("KEAD 동기화 실패: {}", e.getMessage());   // 메시지에 URL·키는 없다
        }
    }
}
