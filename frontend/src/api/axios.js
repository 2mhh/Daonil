import axios from 'axios';
import { API_BASE_URL } from '../config';

const api = axios.create({
  baseURL: API_BASE_URL, // 백엔드 API 서버 주소 (환경변수 VITE_API_URL, src/config.js 참고)
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use((config) => {
  // ✅ 토큰 찾기: authToken(OAuth2) > accessToken(로그인) > token(기존) 순서로 우선순위
  const token = localStorage.getItem('authToken')
      || localStorage.getItem('accessToken')
      || sessionStorage.getItem('accessToken')
      || localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
    (response) => response,
    (error) => {
      // 401 Unauthorized 에러 시 토큰 제거
      if (error.response?.status === 401) {
        localStorage.removeItem('authToken');
        localStorage.removeItem('accessToken');
        sessionStorage.removeItem('accessToken');
        // 필요 시 로그인 페이지로 리다이렉트
        // window.location.href = '/login';
      }
      return Promise.reject(error);
    }
);

export default api;