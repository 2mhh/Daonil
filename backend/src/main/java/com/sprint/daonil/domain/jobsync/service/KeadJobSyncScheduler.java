package com.sprint.daonil.domain.jobsync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 외부 공고 동기화 스케줄러. 기본은 꺼져 있고 운영에서만 KEAD_SYNC_ENABLED=true 로 켠다.
 * 실행 시각은 KEAD_SYNC_CRON (기본 매일 03:00, 서울 시간).
 *
 * 한계: 겹침 방지(AtomicBoolean)는 한 서버 안에서만 유효하다. 서버가 여러 대면 각 서버가 실행하므로
 *      그때는 DB 락(또는 ShedLock) 같은 분산 락이 필요하다. 현재 배포는 단일 인스턴스로 시작하는 구성이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kead.sync.enabled", havingValue = "true")
public class KeadJobSyncScheduler {

    private final KeadJobSyncService syncService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "${kead.sync.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    public void run() {
        if (!running.compareAndSet(false, true)) {
            log.warn("이전 KEAD 동기화가 아직 실행 중이라 이번 실행은 건너뜁니다.");
            return;
        }
        try {
            syncService.sync();
        } catch (Exception e) {
            // 예외가 밖으로 나가도 다음 스케줄은 계속되지만, 원인을 남기고 정리한다. (메시지에 URL·키는 없다)
            log.error("KEAD 동기화 실패: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        } finally {
            running.set(false);
        }
    }
}
