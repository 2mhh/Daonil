package com.sprint.daonil.domain.jobsync.repository;

import com.sprint.daonil.domain.jobsync.entity.JobPostingRaw;
import com.sprint.daonil.domain.jobsync.entity.RawStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface JobPostingRawRepository extends JpaRepository<JobPostingRaw, Long> {

    List<JobPostingRaw> findBySourceAndExternalIdIn(String source, Collection<String> externalIds);

    /** 정제 대상 조회: id 커서 방식 (상태를 바꾸며 처리해도 건너뛰지 않도록 OFFSET을 쓰지 않는다) */
    List<JobPostingRaw> findTop100BySourceAndStatusInAndRawIdGreaterThanOrderByRawIdAsc(
            String source, Collection<RawStatus> statuses, Long rawId);

    /**
     * 오래된 Raw 삭제: cutoff 이전에 마지막으로 확인됐고, 연결된 서비스 공고가 열려 있지 않은 행만 지운다.
     * (서비스 공고(job_posting)는 북마크·지원 이력이 참조하므로 지우지 않는다. 열려 있는 공고의 Raw 는 보존한다.)
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM JobPostingRaw r WHERE r.source = :source AND r.lastSeenAt < :cutoff "
            + "AND NOT EXISTS (SELECT 1 FROM JobPosting jp WHERE jp.source = r.source "
            + "AND jp.externalId = r.externalId AND jp.isClosed = false)")
    int purgeStale(@Param("source") String source, @Param("cutoff") LocalDateTime cutoff);

    long countBySource(String source);

    long countBySourceAndStatus(String source, RawStatus status);
}
