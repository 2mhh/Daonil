package com.sprint.daonil.domain.jobsync.service;

import com.sprint.daonil.domain.jobsync.client.KeadApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeadJobSyncSchedulerTest {

    @Mock KeadJobSyncService syncService;

    private KeadJobSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new KeadJobSyncScheduler(syncService);
    }

    @Test
    void 동기화를_실행한다() {
        scheduler.run();

        verify(syncService).sync();
    }

    @Test
    void 동기화가_실패해도_예외를_밖으로_던지지_않고_다음_실행이_가능하다() {
        when(syncService.sync()).thenThrow(new KeadApiException("KEAD API HTTP 500 (page=1)")).thenReturn(null);

        assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();
        assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();   // 실행 중 표시가 풀려 다시 실행된다

        verify(syncService, times(2)).sync();
    }

    @Test
    void 이전_실행이_끝나지_않았으면_겹쳐_실행하지_않는다() {
        // sync 안에서 스케줄러가 다시 호출되는 상황(= 이전 실행이 아직 진행 중)을 흉내낸다
        when(syncService.sync()).thenAnswer(invocation -> {
            scheduler.run();   // 겹친 호출 → 건너뛰어야 한다
            return null;
        });

        scheduler.run();

        verify(syncService, times(1)).sync();
    }
}
