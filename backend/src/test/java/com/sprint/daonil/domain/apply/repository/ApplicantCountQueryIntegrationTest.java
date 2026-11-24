package com.sprint.daonil.domain.apply.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 DB(application.properties 의 로컬 MySQL)에서, 한 번에 집계한 지원자 수가
 * 기존의 공고별 개별 집계와 같은 값인지 확인한다. 테스트 데이터는 트랜잭션 롤백으로 남지 않는다.
 */
@SpringBootTest
@Transactional
class ApplicantCountQueryIntegrationTest {

    @Autowired ApplicationRepository applicationRepository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 일괄_집계는_공고별_개별_집계와_같다() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0"); // 테스트용 가짜 id 를 쓰기 위해 이 연결에서만 끈다
        try {
            long a = 900_001L, b = 900_002L, none = 900_003L;
            // 기존 개별 집계(countByJobPosting_JobPostingId)는 job_posting 과 조인하므로 공고 행도 있어야 같은 조건이 된다
            for (long id : new long[]{a, b, none}) {
                jdbc.update("INSERT INTO job_posting (job_posting_id, company_id, title) VALUES (?, 1, 'test')", id);
            }
            String sql = "INSERT INTO application (job_posting_id, member_id, resume_id) VALUES (?, ?, 1)";
            jdbc.update(sql, a, 1); jdbc.update(sql, a, 2); jdbc.update(sql, a, 3);
            jdbc.update(sql, b, 1);

            Map<Long, Integer> grouped = applicationRepository.countsByJobPostingIds(List.of(a, b, none));

            assertThat(grouped.getOrDefault(a, 0)).isEqualTo(3).isEqualTo(applicationRepository.countByJobPosting_JobPostingId(a));
            assertThat(grouped.getOrDefault(b, 0)).isEqualTo(1).isEqualTo(applicationRepository.countByJobPosting_JobPostingId(b));
            assertThat(grouped.getOrDefault(none, 0)).isZero().isEqualTo(applicationRepository.countByJobPosting_JobPostingId(none));
            assertThat(applicationRepository.countsByJobPostingIds(List.of())).isEmpty();
        } finally {
            jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }
}
