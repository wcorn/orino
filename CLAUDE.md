# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Structure

```
orino/
├── be/      # Spring Boot 백엔드
├── fe/      # React 프론트엔드 (스펙 미정)
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

Current error codes:
- `GLB-ERR-001`: Bad Request (400)
- `GLB-ERR-002`: Method Not Allowed (405)
- `GLB-ERR-003`: Internal Server Error (500)

### Key Infrastructure

- **JPA Auditing**: Enabled — entities can use `@CreatedDate`/`@LastModifiedDate`
- **OpenAPI/Swagger**: `/swagger-ui.html`
- **Actuator**: `/actuator/health`
- **TestContainers**: Tests use real MySQL 8.4.4 (no H2)

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

## Git Workflow

- main 브랜치에 직접 push 금지. 반드시 새 브랜치에서 PR을 통해 머지한다.

## GitHub Templates

Issue/PR 생성 시 `.github/` 템플릿을 따른다.

- **Feature Issue**: `.github/ISSUE_TEMPLATE/feature.md` — label: `feat`, Description + Todo 체크리스트
- **Bug Issue**: `.github/ISSUE_TEMPLATE/bug.md` — label: `bug`, Description
- **PR**: `.github/PULL_REQUEST_TEMPLATE.md` — 연관 이슈 체크리스트 + 작업 내용

## Commit Message Format

커밋 작성 시 `.gitmessage.txt`를 참고한다.

- 형식: `<타입> : <제목>` (제목 50자 이내, 끝에 마침표 금지)
- 본문: 구체적인 내용, `-`로 구분 (한 줄 72자 이내)
- 꼬릿말: 관련 이슈 번호 (예: `#7`)
- Types: `feat`, `fix`, `docs`, `test`, `refactor`, `style`, `chore`
