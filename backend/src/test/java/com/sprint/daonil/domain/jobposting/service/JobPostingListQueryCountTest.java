package com.sprint.daonil.domain.jobposting.service;

import com.sprint.daonil.domain.jobposting.dto.JobPostingResponseDto;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 목록 조회 1번에 나가는 SQL 개수가 페이지 크기와 무관하게 일정한지 확인한다 (회귀 방지).
 * 개선 전에는 size + 2 개였다 (size=100 이면 102개, docs/TROUBLESHOOTING.md 9번).
 * 로컬 MySQL(application.properties)에 공고가 10건 이상 있어야 의미가 있어, 없으면 건너뛴다.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class JobPostingListQueryCountTest {

    @Autowired JobPostingService service;
    @Autowired EntityManagerFactory emf;

    @Test
    void 목록_조회의_쿼리_수는_페이지_크기와_무관하다() {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

        stats.clear();
        Page<JobPostingResponseDto> page = service.getJobPostings(
                null, null, null, null, null, null, null, null, null, null, "latest", PageRequest.of(0, 100));
        long queries = stats.getPrepareStatementCount();

        assumeTrue(page.getNumberOfElements() >= 10, "로컬 DB에 공고가 10건 미만이라 건너뜀");
        // 공고 목록 1 + 전체 건수 1 + 지원자 수 일괄 집계 1 = 3
        assertThat(queries).as("size=%d 요청의 SQL 수", page.getNumberOfElements()).isEqualTo(3);
    }
}
