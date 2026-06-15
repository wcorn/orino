FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
# CACHEBUST(빌드마다 변하는 값)을 RUN에서 참조해 이 레이어 캐시를 무효화한다.
# 그러지 않으면 gha 레이어 캐시가 패치 공개 전의 apk upgrade 결과를 재사용해 신규 CVE를 흡수하지 못한다.
ARG CACHEBUST=unknown
RUN echo "cachebust ${CACHEBUST}" && apk update && apk upgrade --no-cache && rm -rf /var/cache/apk/*
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
