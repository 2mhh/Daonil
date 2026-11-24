// 백엔드 API 주소 (환경변수 VITE_API_URL)
//
//  - 개발(npm run dev):  VITE_API_URL 이 없으면 http://localhost:8080
//  - 운영(npm run build): VITE_API_URL 을 빌드할 때 지정한다. 없으면 '' (같은 출처 — 프론트와 API 를 같은 도메인에서 서비스할 때)
//
// ※ VITE_ 로 시작하는 값은 빌드 시점에 번들 안에 박힌다. 주소를 바꾸려면 다시 빌드해야 하고,
//    브라우저에서 누구나 볼 수 있으므로 비밀 값(키, 비밀번호)은 절대 넣지 않는다.
const rawApiUrl = import.meta.env.VITE_API_URL ?? (import.meta.env.DEV ? 'http://localhost:8080' : '');

export const API_BASE_URL = rawApiUrl.replace(/\/+$/, '');

/** API 경로를 절대 주소로 만든다. 예) apiUrl('/members/login') */
export const apiUrl = (path) => `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`;
