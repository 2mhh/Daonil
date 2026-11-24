-- 지원자순 정렬 12건
SELECT jp.job_posting_id FROM job_posting jp LEFT JOIN application a ON a.job_posting_id = jp.job_posting_id JOIN company c ON c.company_id = jp.company_id WHERE jp.is_closed = 0 AND (jp.application_end_date IS NULL OR jp.application_end_date >= CURDATE()) GROUP BY jp.job_posting_id ORDER BY COUNT(a.application_id) DESC, jp.created_at DESC LIMIT 12;
