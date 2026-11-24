package com.sprint.daonil.domain.jobposting.service;

import com.sprint.daonil.domain.jobposting.repository.JobPostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class ViewCountBufferTest {

    private JobPostingRepository repo;
    private ViewCountBuffer buffer;

    @BeforeEach
    void setUp() {
        repo = mock(JobPostingRepository.class);
        buffer = new ViewCountBuffer(repo, mock(PlatformTransactionManager.class));
    }

    @Test
    void 조회를_모아서_한_번의_UPDATE로_반영한다() {
        buffer.increment(7L);
        buffer.increment(7L);
        buffer.increment(7L);
        buffer.increment(9L);
        assertThat(buffer.pendingOf(7L)).isEqualTo(3);

        buffer.flush();

        verify(repo, times(1)).addViewCount(7L, 3);
        verify(repo, times(1)).addViewCount(9L, 1);
        assertThat(buffer.pendingOf(7L)).isZero();
    }

    @Test
    void 반영할_것이_없으면_UPDATE를_보내지_않는다() {
        buffer.increment(7L);
        buffer.flush();
        clearInvocations(repo);

        buffer.flush();

        verify(repo, never()).addViewCount(anyLong(), anyLong());
    }

    @Test
    void 반영에_실패하면_값을_되돌려_다음_주기에_다시_시도한다() {
        buffer.increment(7L);
        buffer.increment(7L);
        when(repo.addViewCount(7L, 2)).thenThrow(new RuntimeException("db down")).thenReturn(1);

        buffer.flush();
        assertThat(buffer.pendingOf(7L)).isEqualTo(2);

        buffer.flush();
        assertThat(buffer.pendingOf(7L)).isZero();
        verify(repo, times(2)).addViewCount(7L, 2);
    }

    @Test
    void 동시에_증가시켜도_합계가_맞다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        for (int i = 0; i < 16; i++) {
            pool.submit(() -> { for (int j = 0; j < 1000; j++) buffer.increment(1L); });
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(buffer.pendingOf(1L)).isEqualTo(16_000);
    }
}
