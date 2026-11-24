package com.sprint.daonil.domain.apply.repository;


import com.sprint.daonil.domain.apply.entity.Application;
import com.sprint.daonil.domain.apply.eunmtype.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    // 중복 지원 체크용 메서드
    boolean existsByMember_MemberIdAndJobPosting_JobPostingId(Long memberId, Long jobPostingId);

    // 로그인한 사용자의 모든 지원 목록 조회
    Page<Application> findByMember_LoginIdOrderByAppliedAtDesc(String loginId, Pageable pageable);

    // 로그인한 사용자 지원 상태 필터 적용 조회
    Page<Application> findByMember_LoginIdAndStatusOrderByAppliedAtDesc(String loginId, Status status, Pageable pageable);
    Page<Application> findByMember_LoginIdAndStatus(String loginId, Status status, Pageable pageable);

    // 공고별 지원자 목록 조회 (기업용) - Fetch Join 적용
    @org.springframework.data.jpa.repository.Query("SELECT a FROM Application a " +
           "JOIN FETCH a.member " +
           "JOIN FETCH a.resume " +
           "WHERE a.jobPosting.jobPostingId = :jobPostingId " +
           "ORDER BY a.appliedAt DESC")
    java.util.List<Application> findByJobPosting_JobPostingIdWithMemberAndResume(@org.springframework.data.repository.query.Param("jobPostingId") Long jobPostingId);

    // 공고별 지원자 수 조회
    int countByJobPosting_JobPostingId(Long jobPostingId);

    // 여러 공고의 지원자 수를 한 번에 집계 (목록 조회의 N+1 제거). 지원자가 없는 공고는 결과에 나오지 않는다.
    @org.springframework.data.jpa.repository.Query("SELECT a.jobPosting.jobPostingId, COUNT(a) FROM Application a " +
           "WHERE a.jobPosting.jobPostingId IN :ids GROUP BY a.jobPosting.jobPostingId")
    java.util.List<Object[]> countGroupedByJobPostingIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);

    /** 공고 id → 지원자 수. 지원자가 없는 공고는 0 으로 채우지 않으므로 조회하는 쪽에서 getOrDefault(id, 0) 로 읽는다. */
    default java.util.Map<Long, Integer> countsByJobPostingIds(java.util.Collection<Long> ids) {
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<Long, Integer> counts = new java.util.HashMap<>();
        for (Object[] row : countGroupedByJobPostingIds(ids)) {
            counts.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

}