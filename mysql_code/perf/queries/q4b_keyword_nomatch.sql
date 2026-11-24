-- 일치하는 공고가 없는 키워드 검색 (최악: 끝까지 읽음)
SELECT jp.*, c.* FROM job_posting jp JOIN company c ON c.company_id = jp.company_id WHERE jp.is_closed = 0 AND (jp.application_end_date IS NULL OR jp.application_end_date >= CURDATE()) AND (jp.title LIKE '%없는검색어zz%' OR jp.content LIKE '%없는검색어zz%' OR c.company_name LIKE '%없는검색어zz%') ORDER BY jp.created_at DESC LIMIT 12;
