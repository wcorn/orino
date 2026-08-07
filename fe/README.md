# fe

React 프론트엔드.

## 버전

| 항목       | 버전   | 선택 이유                                             |
| ---------- | ------ | ----------------------------------------------------- |
| Node.js    | 22 LTS | 런타임은 LTS 우선 — CI·Dockerfile·`engines`가 모두 22 |
| React      | 19.2.x | 최신 stable                                           |
| Vite       | 7.3.x  | 최신 stable (8 beta 제외) — pre-release 미사용 원칙   |
| TypeScript | 5.7.x  | 최신 stable                                           |

## 실행

```bash
cp .env.example .env.development   # 최초 1회
npm install
npm run dev      # 개발 서버 (port 3000, /api → localhost:8080 프록시)
```

`npm run dev`는 BE를 `http://localhost:8080`으로 프록시한다. BE는 **리포 루트**의
`docker-compose.yml`로 의존 서비스를 띄운 뒤 실행한다.

```bash
docker compose up -d mysql redis   # 리포 루트에서 (compose 파일 위치)
cd be && ./gradlew bootRun
```

루트 `Makefile`의 `make local`은 위 compose + `npm run dev`를 한 번에 실행한다.

## 스크립트

| 명령                   | 설명                                                   |
| ---------------------- | ------------------------------------------------------ |
| `npm run dev`          | Vite 개발 서버 (HMR)                                   |
| `npm run build`        | 프로덕션 빌드 (`tsc -b && vite build`, 산출물 `dist/`) |
| `npm run preview`      | 빌드 결과물을 로컬에서 미리보기                        |
| `npm test`             | Vitest 단위·통합 테스트                                |
| `npm run test:watch`   | Vitest watch 모드                                      |
| `npm run test:e2e`     | Playwright E2E (`e2e/`)                                |
| `npm run test:e2e:ui`  | Playwright UI 모드                                     |
| `npm run lint`         | ESLint                                                 |
| `npm run format`       | Prettier 자동 수정                                     |
| `npm run format:check` | Prettier 검사 (CI)                                     |

## 환경변수

| 변수            | 용도                      | dev 기본값 | prod                                            |
| --------------- | ------------------------- | ---------- | ----------------------------------------------- |
| `VITE_API_URL`  | BE API base URL           | `/api`     | `https://api.orino.dev/api` (하드코딩 fallback) |
| `VITE_FARO_URL` | Grafana Faro receiver URL | 없음       | `https://telemetry.orino.dev/collect`           |
| `VITE_FARO_KEY` | Faro API key              | 없음       | SealedSecret으로 주입                           |

- dev에서 `VITE_API_URL=/api`로 두면 `vite.config.ts`의 proxy가
  `/api/*` 요청을 `http://localhost:8080`으로 포워딩한다.
- `VITE_FARO_URL` / `VITE_FARO_KEY` 둘 중 하나라도 비어있으면 Faro
  초기화를 생략한다. 앱 동작에는 영향 없음.

## Grafana Faro (관측성)

자동 수집:

- 에러 (`window.error`, `unhandledrejection`, React `ErrorBoundary`)
- console (`captureConsole: true`)
- Web Vitals (LCP, FID, CLS, INP, TTFB)
- 라우트 변경 (page view 이벤트)
- fetch/XHR 트레이스 + `API_BASE_URL`에 W3C `traceparent` 헤더 전파

수동 API:

```ts
import { faro } from "@grafana/faro-react";

faro?.api?.pushLog(["사용자 행동"], { level: "info" });
faro?.api?.pushError(error, { context: { extra: "..." } });
```

초기화 코드: `src/shared/faro/init.ts`. env 미설정 또는 SDK 오류 시
`console.warn`만 출력하고 앱은 정상 동작한다.

## Docker 빌드

```bash
docker build -t orino-fe .
```

`fe/Dockerfile`은 multi-stage (Node alpine builder → nginx alpine 런타임).
런타임 nginx는 SPA fallback을 위해 `nginx.conf`에서 `try_files $uri /index.html`을
적용한다.

prod 환경 변수는 빌드 시점에 vite가 인라인하므로, 환경별로 다른 이미지를
빌드하거나 빌드 시 `--build-arg`로 주입하는 패턴이 필요하다 (현재는 기본값으로
빌드).
