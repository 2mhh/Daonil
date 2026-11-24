-- 기본 목록: 접수 중 공고 최신순 12건
SELECT jp.*, c.* FROM job_posting jp JOIN company c ON c.company_id = jp.company_id WHERE jp.is_closed = 0 AND (jp.application_end_date IS NULL OR jp.application_end_date >= CURDATE()) ORDER BY jp.created_at DESC LIMIT 12;
