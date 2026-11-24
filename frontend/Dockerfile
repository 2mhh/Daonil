# 빌드 단계: Vite 로 정적 파일 생성
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
# package-lock.json 이 package.json 과 어긋나 있으면 npm ci 가 실패하므로 npm install 사용
RUN npm install
COPY . .

# 브라우저가 호출할 백엔드 주소. 빌드할 때 번들에 박힌다 (src/config.js 참고). 비밀 값을 넣지 않는다.
ARG VITE_API_URL=""
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build

# 실행 단계: nginx 로 정적 파일 제공
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
