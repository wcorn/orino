# fe

React 프론트엔드 — MinIO 정적 웹 호스팅

## 버전

| 항목 | 버전 | 선택 이유 |
|------|------|---------|
| Node.js | 24 LTS | Java 25 LTS 선택 기준과 동일 — 런타임은 LTS 우선 |
| React | 19.2.x | Spring Boot 4.0.3과 동일 — 최신 stable |
| Vite | 7.3.x | 최신 stable (8 beta 제외) — pre-release 미사용 원칙 |
| TypeScript | 5.7.x | 최신 stable |

## 실행

```bash
cp .env.example .env.development   # 최초 1회
npm install
npm run dev      # 개발 서버 (port 3000, /api → localhost:8080 프록시)
npm run build    # 프로덕션 빌드 (dist/)
```

## 환경변수

| 변수 | 설명 |
|------|------|
| `VITE_API_URL` | 백엔드 API 주소 (dev: `http://localhost:8080`) |

## MinIO 배포

빌드 시 `VITE_API_URL`을 주입하고, `dist/`를 MinIO 버킷에 업로드합니다.

```bash
docker build \
  --build-arg VITE_API_URL=https://api.orino.example.com \
  --build-arg MINIO_ALIAS=minio \
  --build-arg MINIO_BUCKET=orino-fe \
  .
```

### MinIO 버킷 설정 (최초 1회)

SPA 라우팅을 위해 error document를 `index.html`로 설정해야 합니다.

```bash
mc mb minio/orino-fe
mc website set minio/orino-fe --index index.html --error index.html
mc anonymous set download minio/orino-fe
```
