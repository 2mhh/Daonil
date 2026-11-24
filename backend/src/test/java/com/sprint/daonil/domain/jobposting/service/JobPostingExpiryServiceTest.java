package com.sprint.daonil.domain.jobposting.service;

import com.sprint.daonil.domain.jobposting.repository.JobPostingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostingExpiryServiceTest {

    @Mock JobPostingRepository jobPostingRepository;

    @Test
    void 기준일을_넘겨_만료_공고를_마감하고_건수를_돌려준다() {
        LocalDate today = LocalDate.of(2026, 11, 24);
        when(jobPostingRepository.closeExpiredJobPostings(today)).thenReturn(4);

        int closed = new JobPostingExpiryService(jobPostingRepository).closeExpired(today);

        assertThat(closed).isEqualTo(4);
        verify(jobPostingRepository).closeExpiredJobPostings(today);
    }
}
