-- 직군+지역 필터 최신순 12건
SELECT jp.*, c.* FROM job_posting jp JOIN company c ON c.company_id = jp.company_id WHERE jp.is_closed = 0 AND (jp.application_end_date IS NULL OR jp.application_end_date >= CURDATE()) AND jp.main_category = '사무 종사자' AND jp.work_region LIKE '%서울%' ORDER BY jp.created_at DESC LIMIT 12;
