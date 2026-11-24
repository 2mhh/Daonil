// 상대 경로 API 호출에 백엔드 주소를 적용한다. (main.jsx 에서 가장 먼저 불러온다)
//
// 배경: fetch('/members/login'), axios.get('/api/alarms') 처럼 상대 경로로 호출하는 코드가 여러 파일에 흩어져 있다.
//       개발 서버(Vite)의 프록시(vite.config.js) 덕분에 개발에서는 동작하지만,
//       프론트와 API 의 주소가 다른 운영에서는 요청이 프론트 서버로 가서 실패한다.
//
// VITE_API_URL 을 지정한 경우에만 적용한다.
//  - 지정 안 함(개발 기본): 기존과 같이 개발 서버 프록시를 쓴다. / 지정 안 함(운영): 같은 출처로 간주해 그대로 둔다.
//  - 지정함: axios 기본 주소를 설정하고, 상대 경로 fetch 호출 앞에 주소를 붙인다.
import axios from 'axios';
import { API_BASE_URL, apiUrl } from './config';

if (import.meta.env.VITE_API_URL) {
  axios.defaults.baseURL = API_BASE_URL;

  const nativeFetch = window.fetch.bind(window);
  window.fetch = (input, init) =>
    typeof input === 'string' && input.startsWith('/') && !input.startsWith('//')
      ? nativeFetch(apiUrl(input), init)
      : nativeFetch(input, init);
}
