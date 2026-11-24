-- 깊은 페이지 (OFFSET 10000)
SELECT jp.*, c.* FROM job_posting jp JOIN company c ON c.company_id = jp.company_id WHERE jp.is_closed = 0 AND (jp.application_end_date IS NULL OR jp.application_end_date >= CURDATE()) ORDER BY jp.created_at DESC LIMIT 12 OFFSET 10000;
