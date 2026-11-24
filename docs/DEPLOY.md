# 다온일 배포 가이드 (서버 1대 + Docker Compose, 도메인 없이 공개 IP)

> 이 구성은 **시연·검증용**입니다 (HTTP, 공개 IP). 소셜 로그인(Google/Naver)은 도메인과 HTTPS가 필요해 제외합니다.
> 코드의 `localhost` 하드코딩을 환경변수로 바꾼 뒤의 구성입니다. 로컬 확인 결과는 아래 "검증 결과"를 참고하세요.

## 1. 구성
```
브라우저 ──▶ [서버 공개 IP:80]   frontend (nginx, 정적 파일)
        └──▶ [서버 공개 IP:8080] backend  (Spring Boot) ──▶ mysql (내부망, 외부 비공개)
                                      └─ 업로드 파일: 볼륨 /data/uploads
```
- 프론트와 백엔드는 **포트가 달라 브라우저 기준 다른 출처**입니다. 그래서 ① 프론트가 백엔드 주소를 알아야 하고(`VITE_API_URL`), ② 백엔드가 프론트 출처를 허용해야 합니다(`APP_CORS_ALLOWED_ORIGINS`).

## 2. 환경변수 한눈에 보기
| 변수 | 쓰는 곳 | 의미 | 어긋나면 생기는 증상 |
|---|---|---|---|
| `PUBLIC_API_URL` → `VITE_API_URL` | 프론트 **빌드 시점** | 브라우저가 호출할 백엔드 주소 (예: `http://1.2.3.4:8080`) | 화면은 뜨는데 데이터가 비어 있음(호출이 엉뚱한 곳으로 감) |
| `FRONTEND_ORIGIN` → `APP_CORS_ALLOWED_ORIGINS` | 백엔드 | 허용할 프론트 주소 (예: `http://1.2.3.4`) | 브라우저 콘솔에 CORS 오류, 데이터가 안 옴 |
| `FRONTEND_ORIGIN` → `APP_FRONTEND_URL` | 백엔드 | 소셜 로그인 후 돌아갈 프론트 주소 | 로그인 후 엉뚱한 주소로 이동 |
| `APP_UPLOAD_DIR` | 백엔드 | 업로드 파일 저장 폴더 (컨테이너는 `/data/uploads/`) | 이미지·첨부 저장 실패, 재배포하면 파일이 사라짐 |
| `MYSQL_ROOT_PASSWORD`, `JWT_SECRET` | DB·백엔드 | 비밀 값 | — |
| `KEAD_API_KEY`, `KEAD_SYNC_ENABLED`, `APP_JOB_EXPIRY_ENABLED` | 백엔드 | 공공 API 인증키, 동기화·만료 마감 스케줄러 켜기 | 외부 공고가 갱신되지 않음 |

- ⚠ `VITE_API_URL`은 **빌드할 때 번들에 박힙니다.** 값을 바꾸면 프론트를 **다시 빌드**해야 합니다. 비밀 값은 절대 넣지 마세요(브라우저에서 누구나 볼 수 있음).
- 변수 목록과 예시는 `.env.example`(루트)과 `frontend/.env.example`에 있습니다.

## 3. 배포 순서 (Ubuntu 서버)
```bash
# 1) Docker 설치
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER      # 다시 접속

# 2) 코드 받기
git clone <저장소 주소> daonil && cd daonil
git checkout <배포할 브랜치>

# 3) 환경변수 파일 만들기 (서버 공개 IP 로 바꾼다)
cp .env.example .env
nano .env   # MYSQL_ROOT_PASSWORD, JWT_SECRET, PUBLIC_API_URL, FRONTEND_ORIGIN, (KEAD_API_KEY)

# 4) 빌드·실행
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```
- 서버 방화벽(보안 그룹): **22(내 IP만), 80, 8080** 허용. **3306은 열지 않습니다.**
- 첫 기동 때 `mysql_code/daonil_schema.sql`이 한 번 실행됩니다. 스키마를 바꾼 뒤 다시 적용하려면 DB 볼륨을 지우고 다시 올려야 합니다(`docker compose down -v`, **데이터가 사라지므로 주의**).

## 4. 배포 후 점검 (증빙용)
- [ ] `docker compose ps`에서 세 서비스가 모두 Up
- [ ] 브라우저에서 `http://<공개 IP>` 접속 → 메인에 공고가 보임
- [ ] 개발자도구 Network에서 API 호출 주소가 `http://<공개 IP>:8080/...` (localhost 아님)
- [ ] 콘솔에 CORS 오류가 없음
- [ ] 기업 회원으로 공고 등록 → 목록에 표시
- [ ] 이력서 사진 업로드 → 화면에 표시, `docker compose restart backend` 뒤에도 유지(볼륨)
- [ ] (선택) `KEAD_API_KEY`를 넣고 `KEAD_SYNC_ENABLED=true` → 외부 공고 수집

## 5. 문제가 생기면 (증상 → 원인)
| 증상 | 먼저 볼 것 |
|---|---|
| 화면은 뜨는데 목록이 비어 있음 | 개발자도구 Network의 API 주소가 맞는지(`PUBLIC_API_URL`), 빌드 후 주소를 바꿨다면 **재빌드**했는지 |
| 콘솔에 `blocked by CORS policy` | `APP_CORS_ALLOWED_ORIGINS`가 브라우저 주소와 **글자 그대로** 같은지(`http`/`https`, 포트, 끝의 `/` 없음) |
| 백엔드가 계속 재시작 | `docker compose logs backend` — DB 비밀번호, DB 준비 전 기동, 필수 환경변수 확인 |
| 이미지 업로드 실패 | `APP_UPLOAD_DIR`와 볼륨 경로가 같은지 |

## 6. 로컬에서 확인한 결과 (2026-11-24)
| 항목 | 결과 |
|---|---|
| `VITE_API_URL` 지정 후 프론트 빌드 | 번들에 지정한 주소 포함, `localhost` **없음** |
| `VITE_API_URL` 없이 프론트 빌드(같은 출처) | 번들에 `localhost` **없음** |
| 개발 서버, `VITE_API_URL` 지정 | 상대 경로 `fetch('/api/jobs')`가 `http://localhost:8080/api/jobs`로 호출됨 (브라우저 확인) |
| 개발 서버, 지정 안 함 | 기존처럼 개발 서버 프록시 사용, 화면 정상 |
| `APP_CORS_ALLOWED_ORIGINS=https://example.test` | 해당 출처 허용(200), `http://localhost:5173`은 거부(403) |
| `APP_UPLOAD_DIR`을 다른 폴더로 지정 | 그 폴더의 파일이 `/uploads/...`로 제공됨(200) |
| 단위 테스트 | 96개 통과 (CORS 설정, 업로드 경로, 소셜 로그인 리다이렉트 포함) |

> Docker는 이 PC에 없어서 `docker compose` 자체는 아직 실행해 보지 않았습니다. 서버에서 처음 실행할 때 위 점검 항목으로 확인하세요.
