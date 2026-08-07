# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **프로젝트 규범**: 이 저장소의 최상위 규칙은 [`.specify/memory/constitution.md`](.specify/memory/constitution.md)에 있다.
> 이 파일은 그 원칙의 실행 안내서다. 둘이 충돌하면 constitution이 우선한다.

## Repository Structure

```
orino/
├── be/      # Spring Boot 백엔드
├── fe/      # React 프론트엔드
└── infra/   # Kubernetes GitOps
```

## Documentation

- **설계 문서**: GitHub Wiki (https://github.com/wcorn/orino/wiki)에서 관리한다
- **작업 관리**: GitHub Issues로 관리한다
- 코드 저장소에 문서 파일을 두지 않는다

### 자동 관리 규칙

- 설계 변경이 발생하면 GitHub Wiki 문서도 함께 업데이트한다
- 새로운 기능 작업 시 GitHub Issue가 없으면 먼저 생성한다
- Issue/PR 생성 시 assignee를 항상 `wcorn`으로 설정한다

### 엔지니어링 로그 (트러블슈팅 · 성능 개선)

- 트러블슈팅, 성능 개선, 비용 최적화, 장애 대응 등 **문제→원인→조치→결과**가 있는 작업은
  [Engineering Log (Wiki)](https://github.com/wcorn/orino/wiki/Engineering-Log)에 기록한다
- 인덱스 페이지(`Engineering-Log`)에 리스트로 한 줄 추가하고, 항목별 **개별 페이지**(`ELOG-NNN-<slug>`)를 만든다
- 개별 페이지는 전후 흐름이 드러나게 작성한다: 상황(Before) → 원인 분석 → 조치 → 결과(After) → 교훈 → 관련 이슈/PR
- 템플릿은 인덱스 페이지 하단을 참고한다

## Backend (be/)

**Spring Boot 4.0.7 / Java 25**, Gradle Groovy DSL, 멀티모듈
(`orino-app-api`, `orino-core-web`, `orino-domain-rdb`, `orino-domain-redis`, `orino-common`)

```bash
cd be
./gradlew build          # Build (test + checkstyle 포함)
./gradlew bootRun        # Run
./gradlew test           # Run all tests
./gradlew check          # 테스트 + Checkstyle (maxWarnings = 0)
./gradlew clean build    # Clean and rebuild
```

> Jackson은 3.x다. `ObjectMapper`는 `tools.jackson`에서 import한다 (`com.fasterxml.jackson` 아님).
> 애너테이션만 `com.fasterxml` 네임스페이스를 유지한다.

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

## 품질 게이트 (푸시 전 필수)

테스트만 돌려서는 CI에서 깨지는 문제를 잡지 못한다. 푸시 전에 해당 영역의 게이트를 모두 통과시킨다.

| 영역 | 명령 |
|---|---|
| BE | `cd be && ./gradlew check` (테스트 + Checkstyle, 경고 0) |
| FE | `cd fe && npm run format:check && npm run lint && npm test && npm run build` |
| Infra (YAML) | `yamllint -c .yamllint.yml infra/` |
| Infra (Helm) | `helm lint infra/helm/<app>` · `helm template infra/helm/<app> \| kubeconform -strict` |
| Terraform | `cd infra/terraform && terraform fmt -check -recursive` |

- pre-commit 훅(husky + lint-staged, BE Checkstyle)을 `--no-verify`로 우회하지 않는다.
- CI는 경로별로 나뉜다: `be/**` → BE CI(Gradle build + Trivy), `fe/**` → FE CI,
  `infra/**` → Infra CI(yamllint · helm lint · kubeconform), `infra/terraform/**` → Terraform,
  `infra/ansible/**` → Ansible.
- Trivy 이미지 스캔(CRITICAL/HIGH)은 무시 목록으로 덮지 않고 의존성 버전을 올려 해결한다.

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

### infra/ 하위 구성

| 디렉토리 | 용도 | 적용 경로 |
|---|---|---|
| `argocd/` | Application 정의 (App of Apps) | ArgoCD 동기화 |
| `helm/` | wrapper chart (`values.yaml`에 설정) | ArgoCD 동기화 |
| `terraform/` | AWS 리소스 (Route53, S3 등) | PR=plan, main 머지=자동 apply (OIDC) |
| `ansible/` | note1/note2 노드 부트스트랩 IaC | main 머지 시 자동 apply (tailnet 경유) |

- `terraform apply`를 로컬에서 수동 실행하지 않는다. 정적 AWS 키를 만들지 않는다.
- 노드(`note1`, `note2`)에 허락 없이 설치·설정 변경을 하지 않는다. 변경은 `infra/ansible/`로 코드화한다.
- 컨테이너 안에서 런타임에 패키지를 설치하지 않는다(`apt`, `microdnf`, `awscli` 등). 공식 prebuilt 이미지를 조합한다.
- Helm 변경은 `helm template`으로 렌더링 결과를 확인한 뒤 커밋한다.

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
- **워크트리는 세션마다 고유 이름(`be-fe-{해쉬}`, `infra-{해쉬}`)으로 생성한다.** 고정 이름을 쓰면 여러 세션이 같은 워크트리를 공유해 브랜치·커밋이 꼬인다(한 워크트리엔 브랜치 하나만 체크아웃 가능).
- 워크트리에서 작업 중이면 git 명령도 그 워크트리에서 실행한다. 메인 체크아웃 디렉토리로 `cd`하지 않는다.
- 이미 머지된 PR에는 커밋을 추가하지 않는다. 후속 작업은 새 이슈·새 PR로 연다.

## GitHub Templates

Issue/PR 생성 시 `.github/` 템플릿을 반드시 따른다.

- **Feature Issue**: `.github/ISSUE_TEMPLATE/feature.md` — label: `feat`, Description + Todo 체크리스트
- **Bug Issue**: `.github/ISSUE_TEMPLATE/bug.md` — label: `bug`, Description
- **PR**: `.github/PULL_REQUEST_TEMPLATE.md` — `closes #이슈번호`로 연관 이슈를 연결한다. 연관 이슈가 없으면 먼저 Issue를 생성한다.
- PR에 새로운 커밋이 추가되면 PR 본문도 함께 업데이트한다.

## Commit Message Format

커밋 작성 시 `.gitmessage.txt`를 참고한다.

- 형식: `<타입> : <제목>` (제목 50자 이내, 끝에 마침표 금지)
- 본문: 구체적인 내용, `-`로 구분 (한 줄 72자 이내)
- 꼬릿말: 관련 이슈 번호 (예: `#7`)
- Types: `feat`, `fix`, `docs`, `test`, `refactor`, `style`, `chore`
- 커밋 메시지와 PR 본문에 도구 서명(`Co-Authored-By`, 생성 뱃지 등)을 넣지 않는다.
