FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# 여행 지도 키·Map ID. Vite는 빌드 시점에 값을 박아 넣으므로 런타임 주입이 안 된다.
# 리퍼러 제한이 걸린 공개 키라 번들에 들어가는 것이 정상이다(#1102).
# 비어 있어도 빌드는 되고, 그때는 지도만 빠진다.
ARG VITE_GOOGLE_MAPS_API_KEY=""
ARG VITE_GOOGLE_MAPS_MAP_ID=""
ENV VITE_GOOGLE_MAPS_API_KEY=$VITE_GOOGLE_MAPS_API_KEY
ENV VITE_GOOGLE_MAPS_MAP_ID=$VITE_GOOGLE_MAPS_MAP_ID
RUN npm run build

FROM nginx:alpine
# CACHEBUST(빌드마다 변하는 값)을 RUN에서 참조해 이 레이어 캐시를 무효화한다.
# 그러지 않으면 gha 레이어 캐시가 패치 공개 전의 apk upgrade 결과를 재사용해 신규 CVE를 흡수하지 못한다.
ARG CACHEBUST=unknown
RUN echo "cachebust ${CACHEBUST}" && apk update && apk upgrade --no-cache && rm -rf /var/cache/apk/*
COPY --from=builder /app/dist /usr/share/nginx/html
# nginx.conf=메인(error_log warn), default.conf=server(+access log JSON)
COPY nginx.conf /etc/nginx/nginx.conf
COPY default.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
