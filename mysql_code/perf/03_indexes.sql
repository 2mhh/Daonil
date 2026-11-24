-- 측정 결과 채택한 인덱스. daonil_perf 에만 적용해 측정했다 (서비스 DB daonil 에는 적용하지 않음).
-- 근거와 수치: docs/PERFORMANCE.md
USE daonil_perf;

-- 목록 기본 조회(접수 중 + 최신순), 전체 건수, 깊은 페이지.
-- 처음에는 (is_closed, created_at) 와 (is_closed, application_end_date) 를 따로 만들었지만,
-- 옵티마이저가 커버링 인덱스를 고르지 않아 건수 쿼리가 오히려 느려졌다(16ms → 52ms). 하나로 합쳐 해결.
ALTER TABLE job_posting ADD INDEX idx_jp_open_created (is_closed, created_at, application_end_date);

-- 직군 필터 + 최신순
ALTER TABLE job_posting ADD INDEX idx_jp_cat_created (main_category, created_at);

ANALYZE TABLE job_posting;
