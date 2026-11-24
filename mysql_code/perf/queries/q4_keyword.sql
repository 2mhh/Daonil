-- 키워드 검색 (제목/내용/회사명 LIKE %kw%) 최신순 12건
SELECT jp.*, c.* FROM job_posting jp JOIN company c ON c.company_id = jp.company_id WHERE jp.is_closed = 0 AND (jp.application_end_date IS NULL OR jp.application_end_date >= CURDATE()) AND (jp.title LIKE '%데이터입력%' OR jp.content LIKE '%데이터입력%' OR c.company_name LIKE '%데이터입력%') ORDER BY jp.created_at DESC LIMIT 12;
