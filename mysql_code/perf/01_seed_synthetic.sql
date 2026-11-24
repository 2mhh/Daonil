-- 성능 측정용 합성 데이터 (실제 서비스 데이터가 아님). 서비스 DB(daonil)는 건드리지 않고 별도 스키마 daonil_perf에만 만든다.
-- 사용: SET @N = 30000; 을 먼저 실행한 뒤 이 파일을 source 한다. 매 실행마다 스키마를 새로 만든다.
-- 분포는 실제 외부 공고(daonil)의 값 종류를 따라 만들었다: 직군 7종, 지역 시·구, 근무환경 값, 지원자 수는 일부 공고에 쏠리게 생성.

DROP DATABASE IF EXISTS daonil_perf;
CREATE DATABASE daonil_perf DEFAULT CHARACTER SET utf8mb4;
USE daonil_perf;

-- 구조만 복사 (FK는 복사되지 않음, 보조 인덱스는 daonil 현재 상태 그대로 = PK, uk_job_source_ext, company_id)
CREATE TABLE company     LIKE daonil.company;
CREATE TABLE job_posting LIKE daonil.job_posting;
CREATE TABLE application LIKE daonil.application;

SET SESSION cte_max_recursion_depth = 2000000;
SET @N = IFNULL(@N, 30000);

-- 회사: 공고 10건당 1곳
INSERT INTO company (company_id, member_id, business_number, address, company_name, is_deleted)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < CEILING(@N / 10))
SELECT n, 1, LPAD(n, 10, '0'), '합성 주소', CONCAT('테스트기업', n), 0 FROM seq;

-- 공고
INSERT INTO job_posting
  (company_id, source, title, main_category, sub_category, work_region, employment_type, salary, salary_type,
   recruit_count, application_start_date, application_end_date, is_closed, view_count, created_at, content,
   env_both_hands, env_eyesight, env_hand_work, env_lift_power, env_lstn_talk, env_stnd_walk)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @N)
SELECT
  1 + FLOOR(RAND() * CEILING(@N / 10)),
  'INTERNAL',
  CONCAT(ELT(1 + FLOOR(RAND() * 8), '사무보조', '생산직', '청소', '포장', '조립', '상담', '데이터입력', '배송'), ' 모집 ', n),
  ELT(1 + FLOOR(RAND() * 7), '사무 종사자', '단순노무 종사자', '기능원 및 관련 기능 종사자', '서비스 종사자',
                             '장치·기계조작 및 조립 종사자', '전문가 및 관련 종사자', '판매 종사자'),
  CONCAT('세부직무', 1 + FLOOR(RAND() * 40)),
  CONCAT(ELT(1 + FLOOR(RAND() * 6), '경기', '서울', '충남', '충북', '부산', '대구'), ' ',
         ELT(1 + FLOOR(RAND() * 10), '성남시', '천안시', '중구', '청주시', '용인시', '하남시', '강남구', '구로구', '해운대구', '수성구')),
  ELT(1 + FLOOR(RAND() * 3), '정규직', '계약직', '시간제'),
  2000000 + FLOOR(RAND() * 2000000), '월급',
  1 + FLOOR(RAND() * 5),
  DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND() * 60) DAY),
  -- 약 80%는 아직 접수 중, 20%는 지난 공고
  IF(RAND() < 0.8, DATE_ADD(CURDATE(), INTERVAL FLOOR(RAND() * 60) DAY), DATE_SUB(CURDATE(), INTERVAL 1 + FLOOR(RAND() * 30) DAY)),
  IF(RAND() < 0.1, 1, 0),
  FLOOR(RAND() * 500),
  DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 90 * 24 * 60) MINUTE),
  CONCAT(ELT(1 + FLOOR(RAND() * 6), '성실한 분', '경력 무관', '신입 환영', '교대근무', '주5일', '식사 제공'), ' ', n),
  ELT(1 + FLOOR(RAND() * 4), '양손작업 가능', '한손보조작업 가능', '한손작업 가능', NULL),
  ELT(1 + FLOOR(RAND() * 3), '일상적 활동 가능', '비교적 가는 작업 가능', NULL),
  ELT(1 + FLOOR(RAND() * 3), '양손작업 가능', '한손작업 가능', NULL),
  ELT(1 + FLOOR(RAND() * 3), '5kg 미만', '5~20kg', NULL),
  ELT(1 + FLOOR(RAND() * 3), '일상적 대화 가능', '듣고 말하기 어려움', NULL),
  ELT(1 + FLOOR(RAND() * 3), '오래 서있기 가능', '서거나 걷기 어려움', NULL)
FROM seq;

-- 지원: 공고 수의 약 1.5배, 일부 공고에 쏠리게 (n^2 분포)
INSERT INTO application (job_posting_id, member_id, resume_id, status)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @N * 1.5)
SELECT 1 + FLOOR(POW(RAND(), 2) * @N), 1, 1, 'SUBMITTED' FROM seq;

ANALYZE TABLE company, job_posting, application;
SELECT (SELECT COUNT(*) FROM job_posting) AS job_posting_rows, (SELECT COUNT(*) FROM application) AS application_rows,
       (SELECT COUNT(*) FROM job_posting WHERE is_closed = 0 AND (application_end_date IS NULL OR application_end_date >= CURDATE())) AS open_rows;
