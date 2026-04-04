# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Structure

```
orino/
├── be/      # Spring Boot 백엔드
├── fe/      # React 프론트엔드
└── infra/   # Kubernetes GitOps
```

## Documentation & Project Management

- **설계 문서**: GitHub Wiki (https://github.com/wcorn/orino/wiki)에서 관리한다
- **프로젝트 관리**: GitHub Projects 칸반 보드로 관리한다
- 코드 저장소에 문서 파일을 두지 않는다

### 자동 관리 규칙

- 구현 작업 시 관련 GitHub Issue를 확인하고, 완료 후 프로젝트 보드 상태를 업데이트한다
- 설계 변경이 발생하면 GitHub Wiki 문서도 함께 업데이트한다
- 새로운 기능 작업 시 GitHub Issue가 없으면 먼저 생성한다
- Issue/PR 생성 시 assignee를 항상 `wcorn`으로 설정한다

## Backend (be/)

**Spring Boot 4.0.3 / Java 25**, Gradle Groovy DSL

```bash
cd be
./gradlew build          # Build
./gradlew bootRun        # Run
./gradlew test           # Run all tests
./gradlew clean build    # Clean and rebuild
```

### Profiles

Active profiles: `local` (default, docker-compose), `prod`, `test`. 모두 `mysql`, `actuator`, `redis` 자동 포함.

| Profile | 용도 | MySQL | Redis |
|---------|------|-------|-------|
| `local` | docker-compose 로컬 개발 | `mysql:3306/orino` (dongseok/dongseok) | `redis:6379` |
| `prod`  | 운영 배포 | env vars (`MYSQL_HOST`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`) | env vars (`REDIS_HOST`, `REDIS_PORT`) |
| `test`  | 테스트 | TestContainers MySQL 8.4.4 | — |

### Response & Error Handling

- **Success codes**: `CustomResponseCode` enum in `common/response/api/CustomResponseCode.java`
- **Error codes**: `ErrorCode` enum in `common/response/exception/ErrorCode.java` (format: `GLB-ERR-XXX`)
- **Custom exceptions**: Throw `CustomException(errorCode)` — caught by `GlobalExceptionHandler`

### Key Infrastructure

- **JPA Auditing**: Enabled — entities can use `@CreatedDate`/`@LastModifiedDate`
- **OpenAPI/Swagger**: `/swagger-ui.html`
- **Actuator**: `/actuator/health`
- **TestContainers**: Tests use real MySQL 8.4.4 (no H2)

## Testing

설계 문서: [Test Strategy (Wiki)](https://github.com/wcorn/orino/wiki/Test-Strategy)

- **기능 추가·수정 시 테스트코드를 완벽하게 작성한다.** 각 모듈의 테스트 구조(`support/`)에 맞춰 테스트를 작성하거나 수정한다.
- **기능이 변경·삭제되면 테스트코드도 함께 변경·삭제한다.**
- **테스트코드와 별개로, 로컬에서 해당 기능이 정상 동작하는지 직접 확인한다.** (bootRun, curl, 브라우저 등)

### FE 테스트 원칙

- **Testing Trophy**: 통합 테스트 중심, 단위 테스트는 순수 로직만 선별적으로
- **Mock은 API 레벨에서만**: MSW로 네트워크 응답만 제어한다. 훅/컨텍스트를 `vi.mock`으로 통째 교체하지 않는다
- **프로덕션 코드 그대로 사용**: 테스트 전용 컴포넌트(TestAuthProvider 등)를 만들지 않는다. 테스트 전용 컴포넌트가 필요하다면 프로덕션 구조를 개선해야 한다는 신호
- **테스트 유틸은 OK**: `renderWithRouter` 같은 보일러플레이트 헬퍼는 사용

```bash
cd fe
npm test              # Run all tests (Vitest)
npm run test:watch    # Watch mode
npm run test:e2e      # E2E tests (Playwright)
npm run test:e2e:ui   # E2E tests with UI
```

| | BE | FE |
|---|---|---|
| 단위 | JUnit 5 | Vitest |
| 통합 | MockMvc + TestContainers | RTL + MSW |
| E2E | — | Playwright |

## Infra / GitOps

모든 인프라 설치는 Git을 통해 관리한다 (GitOps). ArgoCD가 클러스터에 설치되어 있으며 App of Apps 패턴을 사용한다.

- **Application** (`infra/argocd/applications/`): Helm 디렉토리를 참조하는 용도만 담는다
- **Helm Chart** (`infra/helm/<app>/`): wrapper chart 구조. `Chart.yaml`에서 `dependencies`로 upstream chart를 참조하고, `values.yaml`에 상세 설정을 정의한다
- 직접 `helm install`이나 `kubectl apply`로 설치하지 않는다

```
infra/helm/<app>/
├── Chart.yaml     # dependencies로 upstream chart 참조
├── values.yaml    # 커스텀 설정
└── Chart.lock     # dependency lock
```

### Secrets 관리

- SealedSecret 원본 비밀값은 `.secrets` 파일에 기록한다 (`.gitignore`로 추적 제외)
- 새 SealedSecret 추가 시 `.secrets`에 원본값을 함께 기록한다
- kubeseal 실행은 `ssh note1`에서 수행한다
  ```bash
  ssh note1 "echo -n '<값>' | kubeseal --raw --namespace <ns> --name <secret-name> \
    --controller-name sealed-secrets --controller-namespace kube-system"
  ```

## Git Workflow

- main 브랜치에 직접 push하거나 merge하지 않는다. 최종 판단은 개발자가 한다.
- 새로운 작업을 시작할 때 반드시 main을 pull 받은 후 새 브랜치를 생성한다.
- 반드시 새 브랜치에서 작업하고 PR을 통해 머지한다.
- PR 생성 시 base 브랜치는 항상 main으로 설정한다. 중간 브랜치를 base로 사용하지 않는다.

## GitHub Templates

Issue/PR 생성 시 `.github/` 템플릿을 반드시 따른다.

- **Feature Issue**: `.github/ISSUE_TEMPLATE/feature.md` — label: `feat`, Description + Todo 체크리스트
- **Bug Issue**: `.github/ISSUE_TEMPLATE/bug.md` — label: `bug`, Description
- **PR**: `.github/PULL_REQUEST_TEMPLATE.md` — `closes #이슈번호`로 연관 이슈를 연결한다. 연관 이슈가 없으면 먼저 Issue를 생성한다.

## Commit Message Format

커밋 작성 시 `.gitmessage.txt`를 참고한다.

- 형식: `<타입> : <제목>` (제목 50자 이내, 끝에 마침표 금지)
- 본문: 구체적인 내용, `-`로 구분 (한 줄 72자 이내)
- 꼬릿말: 관련 이슈 번호 (예: `#7`)
- Types: `feat`, `fix`, `docs`, `test`, `refactor`, `style`, `chore`
