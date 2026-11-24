package com.sprint.daonil.domain.jobsync.repository;

import com.sprint.daonil.domain.jobposting.entity.JobPosting;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 외부 공고 동기화 전용 쿼리. (공용 JobPostingRepository 를 건드리지 않으려고 별도 저장소로 둔다)
 *
 * "이번 실행에서 보였는가"는 Raw 의 last_seen_at 으로 판단한다.
 * 이번 실행 시각(runStartedAt)보다 오래된 last_seen_at → 이번 API 목록에서 사라진 공고.
 */
public interface KeadJobPostingRepository extends Repository<JobPosting, Long> {

    /** 현재 열려 있는(마감되지 않은) 외부 공고 수 */
    @Query("SELECT COUNT(jp) FROM JobPosting jp WHERE jp.source = :source AND jp.isClosed = false")
    long countOpen(@Param("source") String source);

    /** 열려 있지만 이번 실행의 API 목록에서 사라진 공고 수 */
    @Query("SELECT COUNT(jp) FROM JobPosting jp WHERE jp.source = :source AND jp.isClosed = false "
            + "AND jp.externalId IN (SELECT r.externalId FROM JobPostingRaw r "
            + "WHERE r.source = :source AND r.lastSeenAt < :runStartedAt)")
    long countDisappeared(@Param("source") String source, @Param("runStartedAt") LocalDateTime runStartedAt);

    /** 사라진 공고를 마감 처리 */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE JobPosting jp SET jp.isClosed = true, jp.updatedAt = :now "
            + "WHERE jp.source = :source AND jp.isClosed = false "
            + "AND jp.externalId IN (SELECT r.externalId FROM JobPostingRaw r "
            + "WHERE r.source = :source AND r.lastSeenAt < :runStartedAt)")
    int closeDisappeared(@Param("source") String source,
                         @Param("runStartedAt") LocalDateTime runStartedAt,
                         @Param("now") LocalDateTime now);

    /**
     * 마감됐다가 이번 목록에 다시 나타난 공고를 되살린다.
     * 접수 마감일이 이미 지난 공고는 되살리지 않는다 (기간 만료 마감과 번갈아 열렸다 닫히는 것을 막는다).
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE JobPosting jp SET jp.isClosed = false, jp.updatedAt = :now "
            + "WHERE jp.source = :source AND jp.isClosed = true "
            + "AND (jp.applicationEndDate IS NULL OR jp.applicationEndDate >= :today) "
            + "AND jp.externalId IN (SELECT r.externalId FROM JobPostingRaw r "
            + "WHERE r.source = :source AND r.lastSeenAt >= :runStartedAt)")
    int reopenReappeared(@Param("source") String source,
                         @Param("runStartedAt") LocalDateTime runStartedAt,
                         @Param("today") LocalDate today,
                         @Param("now") LocalDateTime now);
}
