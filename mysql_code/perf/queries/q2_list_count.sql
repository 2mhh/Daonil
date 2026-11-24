-- 페이징용 전체 건수
SELECT COUNT(*) FROM job_posting jp WHERE jp.is_closed = 0 AND (jp.application_end_date IS NULL OR jp.application_end_date >= CURDATE());
