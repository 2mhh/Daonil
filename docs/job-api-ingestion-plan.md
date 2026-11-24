# 채용 공고 API 적재·정제 설계 (한국장애인고용공단 '장애인 구인 실시간 현황')

> 초안. 실제 API를 호출해 확인한 사실(239건 전수)을 근거로 작성. **인증키는 이 문서·코드·로그에 넣지 않는다(환경변수 `KEAD_API_KEY`).**

## 1. API 사실 (호출로 확인)
| 항목 | 내용 |
|---|---|
| End Point | `https://apis.data.go.kr/B552583/job2` / 사용 기능 `/job_list_env2` (작업환경 포함) |
| 요청 | `serviceKey`, `pageNo`, `numOfRows` |
| 형식 | **XML** (`response/header/resultCode=0000`, `body/items/item`, `totalCount`) |
| 규모 | **totalCount 239건** → 100건씩 3회 호출. 일일 트래픽 10,000이라 한도 문제 없음 |
| 성격 | "현재 진행 중인 공고"의 **스냅샷**(끝난 공고는 목록에서 사라짐) |

**item 필드**: `busplaName`(사업장명) `compAddr`(사업장 주소) `cntctNo` `regagnName`(등록 지사) `jobNm`(직무명) `empType`(계약직/상용직/시간제) `salaryType`(월급/연봉/시급/일급) `salary`("1,080,000") `termDate`("2026-11-17~2026-12-01") `offerregDt`/`regDt`(yyyyMMdd) `enterType`(무관/경력/신입) `reqEduc`(무관/고졸/초대졸/대졸) `reqCareer`("무관", "0년0개월", "0년개월", "3년0개월") `reqMajor`·`reqLicens`(드묾) `rno`·`rnum` + 작업환경 6종 `envBothHands` `envEyesight` `envHandWork` `envLiftPower` `envLstnTalk` `envStndWalk`

## 2. 데이터에서 발견한 문제 → 설계에 반영
| 발견 | 의미 | 대응 |
|---|---|---|
| **공고 고유 ID가 없음** (`rno`/`rnum`은 응답 내 순번) | 순번은 호출마다 달라질 수 있어 키로 쓸 수 없음 | **자연키 해시**로 `external_id` 생성 |
| 동일 내용 중복 행 (rno 104·105) | 같은 공고가 2번 내려옴 | 해시가 같으면 **하나로 병합**(발생 횟수 기록 불필요) |
| 작업환경은 **40~53%만** 존재 (96~126건/239) | 나머지는 값 없음 | `NULL` 저장, 환경 필터는 NULL 제외 동작 확인 |
| 환경 값 문자열이 **다온일 화면의 선택지와 동일**(시력·들기·듣기말하기 확인) | 변환 없이 저장 가능 | 값이 선택지에 없으면 `NULL`+경고. 손 사용·손 작업·서기·걷기 3종은 프론트 선택지와 대조 필요 |
| 사업장명 최대 47자 | `company.company_name VARCHAR(30)` 초과 | 컬럼을 100으로 확장 |
| 사업자번호 없음 | `company.business_number NOT NULL` | `EXT-<해시>` 형태 자리표시 값 |
| 주소의 시·도 표기 혼재(전라북도/전북특별자치도, 강원도/강원특별자치도, 전남광주통합특별시…) | 지역 필터가 갈라짐 | **지역 정규화 매핑표** |
| 급여가 문자열(쉼표), 유형 4종(시급 121·월급 98·연봉 19·일급 1) | 숫자 변환 필요 | 쉼표 제거 후 int(원 단위), 유형 그대로 저장 |
| 직종 코드 없음, 직무명만 | `main/sub_category` 채울 값 없음 | 기존 `JobCategoryClassifier`(직무명→33개 직업군)로 소분류, 대분류는 기존 SQL의 매핑 규칙 |
| 공고 설명·모집 인원·근무시간 없음 | `content VARCHAR(225)`, `recruit_count` 등 | 공란 허용, `content`는 TEXT로 확장 |

## 3. 흐름
```
@Scheduled(매일 03:00) 또는 관리자 수동 실행
 ① 수집: 1~3페이지 호출 → XML 파싱 → 항목별 external_id·content_hash 계산
        → job_posting_raw upsert (같은 해시면 last_seen_at만 갱신, 내용 변경이면 status=NEW)
        → 검증: 수집 건수 == totalCount, resultCode == 0000
 ② 정제: raw(status=NEW)를 id 커서(WHERE raw_id > :last ORDER BY raw_id LIMIT n)로 읽어
        company upsert → job_posting upsert(필드 매핑·정규화) → status=REFINED / 실패 시 FAILED+사유
 ③ 마감: **이번 수집이 완전 성공일 때만** last_seen_at이 이번 실행보다 오래된 외부 공고를 is_closed=true
 ④ (선택) 신규·변경 공고만 임베딩 비동기 생성
```
- **스냅샷 API라서 ③이 핵심**입니다. 목록에서 사라진 공고를 닫지 않으면 끝난 공고가 계속 노출됩니다. 단, API 오류로 빈/부분 응답이 오면 전부 닫히는 사고가 나므로 "완전 성공 + 직전 대비 급감하지 않음(예: 30% 이상 감소 시 마감 보류)" 조건을 둡니다.
- **OFFSET 페이징 금지**: 정제 대기 목록을 처리하며 상태를 바꾸는 조회에서 OFFSET을 쓰면 일부가 건너뜁니다(프로젝트 1의 Page Drift). `id > 마지막 id` 커서 방식으로 읽습니다.

## 4. 식별(external_id) 설계
- 식별 필드: `busplaName | jobNm | termDate | compAddr` (변하지 않는 값) → SHA-256
- 변경 감지: 전체 필드 `content_hash`. 급여 등이 바뀌면 같은 공고의 **갱신**으로 처리
- 이유: 급여·유형은 수정될 수 있어 식별에 넣으면 "새 공고 + 옛 공고 마감"이 되어 조회수·북마크가 끊김

## 5. 필드 매핑
| 다온일 컬럼 | API 원본 | 변환 |
|---|---|---|
| `title` | `jobNm` | 그대로 (150자 이내) |
| company.`company_name` | `busplaName` | 100자로 확장, 공백 정리 |
| company.`address` | `compAddr` | 그대로 |
| company.`business_number` | 없음 | `EXT-` + 이름·주소 해시 앞 12자 |
| `employment_type` | `empType` | 상용직→(다온일 기존 선택지와 대조해 결정), 계약직, 시간제 |
| `work_region` | `compAddr` 앞부분 | 시·도 정규화 + 시·군·구 (사업장 주소 기준이라는 한계 명시) |
| `salary` / `salary_type` | `salary` / `salaryType` | 쉼표 제거 → int(원), 유형 그대로 |
| `application_start/end_date` | `termDate` | `~`로 분리해 LocalDate |
| `qualification` | `enterType` `reqEduc` `reqCareer` `reqMajor` `reqLicens` | "경력: …, 학력: …, 전공: …, 자격: …" 문장 구성. `0년0개월`/`0년개월`은 "경력 무관" 취급 |
| `env_*` 6종 | 동명 필드 | 선택지와 일치하면 저장, 아니면 NULL |
| `sub_category` / `main_category` | `jobNm` | `JobCategoryClassifier` 최상위 점수, 점수 0이면 "미분류" |
| `content` | 없음 | "문의: {regagnName} {cntctNo}" 정도로 요약(TEXT 확장) |
| `source`, `external_id` | — | `KEAD_JOB2`, 해시 |

## 6. 스키마 변경 (daonil_schema.sql에 반영)
```sql
CREATE TABLE job_posting_raw (
    raw_id        BIGINT NOT NULL AUTO_INCREMENT,
    source        VARCHAR(30) NOT NULL,
    external_id   CHAR(64)    NOT NULL,
    payload       JSON        NOT NULL,
    content_hash  CHAR(64)    NOT NULL,
    status        ENUM('NEW','REFINED','FAILED') NOT NULL DEFAULT 'NEW',
    error_message VARCHAR(500) NULL,
    first_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (raw_id),
    UNIQUE KEY uk_raw_source_ext (source, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE job_posting
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'INTERNAL' AFTER company_id,
    ADD COLUMN external_id CHAR(64) NULL,
    ADD COLUMN external_contact VARCHAR(100) NULL,
    ADD UNIQUE KEY uk_job_source_ext (source, external_id),
    MODIFY content TEXT NULL;

ALTER TABLE company MODIFY company_name VARCHAR(100) NULL;
```
- `JobPosting` 엔티티에 `source`, `externalId`, `externalContact` 필드를 추가하고, `content` 길이 제한(225)을 제거해야 합니다.

## 7. 외부 공고를 다룰 때의 정책 (결정 필요)
1. **회사 귀속 — ✅ 결정됨(시스템 계정 방식)**: 시스템 계정(`system_kead`, 로그인 불가한 임의 비밀번호) 1개를 만들고, 외부 사업장은 그 계정 소유 `company` 행으로 저장. 기존 JOIN·조회 코드를 거의 바꾸지 않고, 수정·마감 권한 검증에서 이 계정만 통과하므로 **외부 공고는 사실상 읽기 전용**.
   - 계정 생성은 코드/시드 SQL로 1회(`role=COMPANY`, `login_id=system_kead`, 비밀번호는 무작위 값을 BCrypt 해시로 저장하고 **어디에도 기록하지 않음** → 로그인 불가).
   - 로그인 화면에서 이 계정으로 로그인이 시도되지 않도록 `login_id`를 예측하기 어려운 값으로 정할지 검토(이중 방어: 서비스에서 `source` 기반으로 외부 공고 수정·마감 거부).
   - 회원 목록·통계에 이 계정이 섞이지 않게 `member.name='시스템(한국장애인고용공단)'` 등으로 식별 가능하게 한다.
2. **지원 방식**: 외부 공고는 앱 내 지원 대신 "문의: 지사명·전화"를 표시(`external_contact`). `source != INTERNAL`이면 지원 API를 막음.
3. **출처 표기**: 화면에 "한국장애인고용공단" 표시(이용허락범위 제한 없음이지만 출처 명시가 안전).
4. **고용 형태 값**: `상용직`을 다온일 선택지(정규직 등)와 어떻게 맞출지 결정.

## 8. 구현 구성 (신규 클래스)
- `KeadJobApiClient` 호출·재시도, `KeadJobXmlParser` XML→DTO(JDK 내장 DOM 파서로 의존성 추가 없이 가능)
- `JobRawRepository`, `JobIngestService`(수집), `JobRefineService`(정제), `JobSyncScheduler`(`@EnableScheduling` 필요 — 현재 없음), 관리자 수동 실행 엔드포인트
- 설정: `kead.api.key`(환경변수), `app.jobsync.enabled`, `app.jobsync.cron`

## 9. 주의 (함정)
- **인증키 인코딩**: 포털이 주는 Encoding 키(`%2F`…)를 그대로 URL에 붙이면 정상, HTTP 클라이언트가 한 번 더 인코딩하면 인증 오류. `URI`를 이미 인코딩된 것으로 취급하도록 구성.
- 키가 들어간 URL을 **로그에 찍지 않기**(프로젝트 1의 한계로 지적한 점).
- XML 인코딩은 UTF-8, 빈 요소·누락 필드(NULL) 처리.
- 호출 실패 시 **부분 결과로 마감 처리하지 않기**(위 ③ 조건).

## 10. 검증 항목
- [ ] 수집 건수 == `totalCount`(239), raw 저장 건수, 병합된 중복 건수
- [ ] 정제 성공/실패 건수, 실패 사유 목록
- [ ] 같은 데이터로 두 번 실행해도 `job_posting` 건수 불변(멱등성)
- [ ] 목록에서 사라진 공고가 다음 실행에서 닫힘(마감 로직)
- [ ] 화면: 외부 공고가 목록·상세에 표시되고, 환경 필터 동작

## 11. 작업 순서
1. 스키마·엔티티 변경 → 2. 수집(Raw) → 3. 정제 → 4. 마감·스케줄 → 5. 화면 표시(출처·문의) → 6. 검증


---

## 12. 구현 현황 (2026-11-24, 로컬 DB에서 검증 완료)
| 단계 | 상태 | 비고 |
|---|---|---|
| 수집(Raw 적재) | 완료 | 239건 수집, 중복 1건 병합, 238행 저장. 재실행 시 신규 0 (멱등) |
| 정제 | 완료 | 238건 → company 205곳 + job_posting 238건, 실패 0, 경고 16(직군 미분류) |
| 마감 | 완료 | 목록에서 사라진 공고 마감 + 다시 나타난 공고 재개. 안전장치(불완전 수집 시 중단, 한도 초과 시 중단) |
| 스케줄러 | 완료 | 동기화(기본 매일 03:00 KST), 접수 기간 만료 마감(기본 매일 00:10 KST). 기본은 꺼짐 |
| 화면 표시(출처·문의처·지원 버튼 대체) | 미구현 | API 응답에 source, externalContact 포함됨 |
| 외부 공고 앱 내 지원 차단 | 미구현 | 수정·마감은 차단됨 |
| 직군 미분류 13개 직무명 보강 | 미구현 | 바리스타, 급식 배식원, 세차원 등 |

### 운영 설정 (환경변수)
| 변수 | 의미 | 기본 |
|---|---|---|
| `KEAD_API_KEY` | 공공데이터포털 인증키 (필수, 저장소·로그에 넣지 않음) | 없음 |
| `KEAD_SYNC_ENABLED` | `true`면 동기화 스케줄러 켜기 | 꺼짐 |
| `KEAD_SYNC_CRON` | 동기화 실행 시각 (Spring cron, 서울 시간) | `0 0 3 * * *` |
| `KEAD_SYNC_RUN_ON_STARTUP` | `true`면 앱 시작 시 1회 실행 (수동 확인용) | 꺼짐 |
| `KEAD_SYNC_MAX_CLOSE_RATIO` | 한 번에 닫을 수 있는 최대 비율 | `0.3` |
| `KEAD_SYNC_MIN_CLOSE_COUNT` | 비율과 무관하게 항상 허용하는 건수 | `10` |
| `KEAD_SYNC_RAW_RETENTION_DAYS` | Raw 보관 기간(일), 0 이하이면 Raw 정리 안 함 | `90` |
| `APP_JOB_EXPIRY_ENABLED` | `true`면 접수 기간 만료 마감 켜기 | 꺼짐 |
| `APP_JOB_EXPIRY_CRON` | 만료 마감 실행 시각 | `0 10 0 * * *` |

### 검증한 시나리오 (실제 MySQL + 실제 API)
1. 정상 실행: 마감 0, 재개 0.
2. 목록에서 사라진 공고(가짜 데이터 삽입) → 이번 실행에서 마감됨, 화면 목록에서 제외, 상세 조회는 `isClosed=true`.
3. 마감된 실제 공고 3건 → API에 다시 나타나 재개됨.
4. 스케줄러: 25초 주기로 설정해 동기화 2회 실행 확인. 접수 기간 만료 마감은 첫 실행에서 4건 마감, 이후 0건.

### 알려진 한계
- 겹침 방지(AtomicBoolean)는 한 서버 안에서만 유효. 서버가 여러 대면 분산 락 필요.
- 마감 판단은 API 한 번의 응답에 의존. 안전장치로 완화했지만 API가 정상 응답(200)으로 일부 데이터만 줄 경우는 건수 비교(totalCount)로만 감지한다.
- Hibernate가 IDENTITY 키 때문에 INSERT를 한 건씩 실행한다 (현재 규모 239건에서는 문제 없음).

---

## 13. Raw 정리 (2026-11-24 추가)
Raw 는 계속 쌓이므로 오래된 행을 정리한다.

- **삭제 조건 (모두 만족)**: ① 마지막으로 API 목록에서 확인된 지 보관 기간(기본 90일)이 지남 ② 연결된 서비스 공고(`job_posting`)가 열려 있지 않음(마감됐거나 없음) ③ 이번 수집이 완전함(수집 건수 == totalCount)
- **지우지 않는 것**: 서비스 공고(`job_posting`)는 삭제하지 않는다 — 북마크·지원 이력이 참조한다. 열려 있는 공고의 Raw 도 보존한다(마감 안전장치로 닫히지 않은 공고일 수 있음).
- **순서**: 수집 → 정제 → 마감 → Raw 정리. 마감이 먼저여야 "방금 마감된 공고의 오래된 Raw"를 같은 실행에서 정리할 수 있다.
- **설정**: `KEAD_SYNC_RAW_RETENTION_DAYS` (기본 90, 0 이하이면 정리 안 함)
- **트레이드오프**: 삭제하면 재정제와 변경 이력 확인 근거가 사라진다. 같은 공고가 보관 기간 뒤에 다시 나타나면 새 Raw 로 다시 만들어지고, 기존 서비스 공고(같은 식별키)를 갱신·재개한다.

### 검증한 시나리오 (실제 MySQL)
| 테스트 데이터 | 1회차(마감 안전장치를 0으로 해 마감 건너뜀) | 2회차(정상) |
|---|---|---|
| R1: Raw 120일 전, 서비스 공고 없음 | **삭제** | - |
| R2: Raw 120일 전, 서비스 공고 마감 | **삭제** | - |
| R3: Raw 30일 전, 서비스 공고 마감 | 보존(보관 기간 이내) | 보존 |
| R5: Raw 120일 전, 서비스 공고 열림 | 보존(열린 공고) | 마감 후 **삭제** |
- 1회차 purged=2, 2회차 closed=1·purged=1. R2·R3·R5의 서비스 공고(job_posting)는 Raw 삭제 후에도 모두 남아 있음을 확인했다. 확인 후 테스트 데이터는 삭제했다.

## 14. 동작 요약 (면접용)
1. 스케줄러가 매일 `RestClient`로 공공 API를 호출해 진행 중인 공고를 `job_posting_raw`에 수집한다.
2. 공고 고유 ID가 없어 **식별키(`external_id`)를 사업장명·직무명·접수기간·주소의 SHA-256으로 직접 설계**했다. Raw 의 기본키는 자동 증가 번호(`raw_id`), `(source, external_id)`는 유니크.
3. 새 공고는 저장, 바뀐 공고는 갱신(재정제 대상), 같은 공고는 확인 시각만 갱신한다 (멱등).
4. 재정제 대상(NEW, 이전 실패 FAILED)을 `company`·`job_posting`으로 생성 또는 갱신한다.
5. 이번 목록에서 사라진 공고는 서비스 공고를 **마감**(삭제 아님)하고, 다시 나타나면 재개한다. 수집이 불완전하거나 한꺼번에 너무 많이 사라지면 마감하지 않는다.
6. 보관 기간이 지난 Raw(연결된 공고가 마감된 것만)를 삭제한다.