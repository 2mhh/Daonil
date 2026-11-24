package com.sprint.daonil.domain.jobposting.service;

import com.sprint.daonil.domain.jobposting.repository.JobPostingRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 공고 조회수를 메모리에 모았다가 주기적으로 한 번에 DB에 더한다.
 *
 * 왜: 상세 조회마다 같은 행을 UPDATE 하면, 같은 공고에 조회가 몰릴 때 행 락 대기로 처리량이 크게 떨어진다
 *     (docs/LOAD_TEST.md). 조회마다 UPDATE 하는 대신 N번을 모아 UPDATE 1번으로 반영한다.
 * 대가: 서버가 비정상 종료되면 아직 반영 전인 조회수(최대 flush 주기만큼)가 사라질 수 있다.
 *       정상 종료 때는 @PreDestroy 에서 남은 값을 반영한다. 서버가 여러 대여도 각 서버가 자기 몫을 더하므로 합은 맞다.
 */
@Slf4j
@Component
public class ViewCountBuffer {

    private final Map<Long, AtomicLong> pending = new ConcurrentHashMap<>();
    private final JobPostingRepository jobPostingRepository;
    private final TransactionTemplate tx;

    public ViewCountBuffer(JobPostingRepository jobPostingRepository, PlatformTransactionManager txManager) {
        this.jobPostingRepository = jobPostingRepository;
        this.tx = new TransactionTemplate(txManager);
    }

    public void increment(Long jobPostingId) {
        pending.computeIfAbsent(jobPostingId, k -> new AtomicLong()).incrementAndGet();
    }

    /** 아직 DB에 반영되지 않은 조회수 (응답에 DB 값과 더해 보여 준다) */
    public long pendingOf(Long jobPostingId) {
        AtomicLong n = pending.get(jobPostingId);
        return n == null ? 0 : n.get();
    }

    @Scheduled(fixedDelayString = "${app.view-count.flush-interval-ms:5000}")
    public void flush() {
        List<Long> ids = List.copyOf(pending.keySet());
        for (Long id : ids) {
            long delta = pending.get(id).getAndSet(0);
            if (delta == 0) continue;
            try {
                tx.executeWithoutResult(s -> jobPostingRepository.addViewCount(id, delta));
            } catch (RuntimeException e) {
                pending.get(id).addAndGet(delta); // 반영 실패 시 되돌려 다음 주기에 다시 시도
                log.warn("조회수 반영 실패, 다음 주기에 재시도: jobPostingId={}, delta={}", id, delta, e);
            }
        }
    }

    @PreDestroy
    void flushOnShutdown() {
        try {
            flush();
        } catch (RuntimeException e) {
            log.warn("종료 시 조회수 반영 실패", e);
        }
    }
}
