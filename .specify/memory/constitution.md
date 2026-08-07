<!--
Sync Impact Report
- Version change: (template, unversioned) → 1.0.0
- Rationale: 최초 비준. 템플릿 플레이스홀더를 실제 프로젝트 원칙으로 전면 대체(MAJOR 초기 릴리스).
- Principles defined (all new):
  - I. 모노레포 경계를 넘지 마라
  - II. 저장소에 문서를 쌓지 마라
  - III. main을 직접 건드리지 마라
  - IV. 커밋·PR 형식을 임의로 바꾸지 마라
  - V. 테스트 없이 기능을 바꾸지 마라
  - VI. 게이트를 통과하지 않은 코드를 푸시하지 마라
  - VII. Git 밖에서 인프라를 바꾸지 마라
- Sections added: 기술 스택 및 구조 제약, 개발 워크플로 및 품질 게이트, Governance
- Sections removed: 템플릿 자리표시자 섹션 전부([SECTION_2_NAME], [SECTION_3_NAME])
- Templates requiring review: .specify/templates/plan-template.md, spec-template.md,
  tasks-template.md — 런타임에 본 문서를 참조하므로 별도 수정 없음(확인 완료)
- Deferred TODOs: 없음
-->

# Orino Constitution

이 문서는 orino 저장소의 최상위 규범이다. 원칙은 "무엇을 해도 된다"가 아니라
**"무엇을 하지 마라"** 로 기술한다. 금지 사항을 위반하는 변경은 리뷰에서 거부된다.

## Core Principles

### I. 모노레포 경계를 넘지 마라

- 저장소 최상위에 `be/`, `fe/`, `infra/` 외의 새 코드 디렉터리를 만들지 마라. 루트는 공용
  설정(`.github/`, `.editorconfig`, `.yamllint.yml`, `docker-compose.yml`, `Makefile`,
  husky/lint-staged 설정)만 담는다.
- 한 모듈의 소스를 다른 모듈에서 직접 참조하지 마라. `fe/`는 HTTP API로만 `be/`와 통신하고,
  `be/`는 `fe/` 빌드 산출물에 의존하지 않는다.
- 배포 매니페스트(Helm chart, ArgoCD Application, Terraform, Ansible)를 `infra/` 밖에 두지 마라.
- `be/` 하위 Gradle 모듈 경계(`orino-app-api`, `orino-core-web`, `orino-domain-rdb`,
  `orino-domain-redis`, `orino-common`)를 우회하는 역방향 의존을 추가하지 마라.

**근거**: CI/CD가 `be/**`, `fe/**`, `infra/**` 경로 필터로 분리 실행된다. 경계가 흐려지면
변경이 잘못된 파이프라인을 타거나 아예 검증되지 않은 채 머지된다.

### II. 저장소에 문서를 쌓지 마라

- 설계 문서·기획서·회의록을 코드 저장소에 파일로 커밋하지 마라. 설계 문서의 SSOT는
  [GitHub Wiki](https://github.com/wcorn/orino/wiki), 작업 관리의 SSOT는 GitHub Issues다.
- 설계를 바꾸면서 Wiki 갱신을 생략하지 마라. 코드와 Wiki는 같은 작업 단위에서 함께 바뀐다.
- 트러블슈팅·성능 개선·비용 최적화·장애 대응처럼 **문제 → 원인 → 조치 → 결과**가 있는 작업을
  기록 없이 끝내지 마라. `Engineering-Log` 인덱스에 한 줄, 개별 페이지(`ELOG-NNN-<slug>`)에
  전후 흐름(Before → 원인 → 조치 → After → 교훈 → 관련 이슈/PR)을 남긴다.
- 예외: `CLAUDE.md`, `.specify/` 산출물, `infra/README.md` 등 도구가 런타임에 읽는 파일은
  문서가 아니라 설정으로 취급한다.

**근거**: 문서가 저장소와 Wiki에 이중으로 존재하면 반드시 한쪽이 낡는다.

### III. main을 직접 건드리지 마라

- `main`에 직접 push하거나 로컬에서 merge하지 마라. 모든 변경은 새 브랜치 → PR을 거친다.
- 새 작업을 이전 브랜치 위에서 시작하지 마라. 반드시 `main`을 pull한 뒤 새 브랜치를 만든다.
- PR의 base를 `main` 이외의 중간 브랜치로 설정하지 마라.
- 이미 머지된 PR에 커밋을 추가하지 마라. 후속 작업은 새 이슈·새 PR로 연다.
- 워크트리에 고정 이름을 쓰지 마라. 세션마다 고유 이름(`be-fe-{해시}`, `infra-{해시}`)으로
  생성한다. 한 워크트리에는 브랜치 하나만 체크아웃되므로 이름을 공유하면 커밋이 꼬인다.
- 워크트리에서 작업 중일 때 메인 체크아웃 디렉터리로 `cd`해 git 명령을 실행하지 마라.
- 머지 여부는 사람이 판단한다. 자동화·에이전트가 PR을 스스로 머지하지 마라.

**근거**: `main`은 CD 트리거다. 직접 push는 곧 검증 없는 배포다.

### IV. 커밋·PR 형식을 임의로 바꾸지 마라

- `.gitmessage.txt` 형식을 벗어난 커밋 메시지를 만들지 마라.
  - 제목: `<타입> : <제목>`, 50자 이내, 명령형, 끝에 마침표 금지.
  - 타입은 `feat`, `fix`, `docs`, `test`, `refactor`, `style`, `chore`만 사용한다.
  - 본문은 `-`로 구분하고 한 줄 72자 이내. 꼬릿말에 관련 이슈 번호(`#7`)를 적는다.
- 커밋 메시지나 PR 본문에 도구 서명(`Co-Authored-By`, 생성 뱃지 등)을 넣지 마라.
- 연관 이슈 없는 PR을 열지 마라. 이슈가 없으면 먼저 생성한다. PR 본문은
  `.github/PULL_REQUEST_TEMPLATE.md`를 따르고 `closes #<번호>`로 이슈를 연결한다.
- 이슈를 템플릿 없이 만들지 마라. Feature는 `.github/ISSUE_TEMPLATE/feature.md`(label `feat`,
  Description + Todo), Bug는 `bug.md`(label `bug`)를 사용한다.
- Issue/PR의 assignee를 `wcorn` 이외로 두거나 비워 두지 마라.
- PR에 커밋을 추가하고 본문 갱신을 생략하지 마라.

**근거**: 커밋·이슈·PR이 릴리스 추적의 유일한 기록이다. 형식이 깨지면 히스토리에서 "왜"가 사라진다.

### V. 테스트 없이 기능을 바꾸지 마라

- 기능을 추가·수정하면서 테스트를 생략하지 마라. 각 모듈의 테스트 구조(`support/`)에 맞춰
  작성하거나 수정한다.
- 기능을 변경·삭제하면서 낡은 테스트를 남겨 두지 마라. 테스트도 함께 변경·삭제한다.
- 테스트만 통과했다고 완료로 보고하지 마라. 로컬에서 실제 동작(bootRun, curl, 브라우저)을
  직접 확인한 뒤 완료로 처리한다.
- BE 테스트를 H2 같은 인메모리 DB로 대체하지 마라. `test` 프로파일은 TestContainers
  MySQL 8.4.4를 사용한다.
- FE에서 훅·컨텍스트를 `vi.mock`으로 통째 교체하지 마라. Mock은 MSW를 통한 API(네트워크)
  레벨에서만 한다.
- FE에 테스트 전용 컴포넌트(`TestAuthProvider` 등)를 만들지 마라. 그런 것이 필요하다면
  프로덕션 구조를 고쳐야 한다는 신호다. `renderWithRouter` 같은 보일러플레이트 헬퍼는 허용한다.
- 순수 로직이 아닌 것을 단위 테스트로 잘게 쪼개지 마라. Testing Trophy에 따라 통합 테스트가
  중심이다.

**근거**: 실행 환경(MySQL 방언, 네트워크 계층)에서만 드러나는 결함이 이 프로젝트의 주요
장애 원인이었다. 실물에 가까운 테스트만이 그것을 잡는다.

### VI. 게이트를 통과하지 않은 코드를 푸시하지 마라

- 테스트만 돌려 보고 푸시하지 마라. FE는 `npm run format:check`와 `npm run lint`, BE는
  `./gradlew check`(Checkstyle 포함)를 함께 통과시킨다. `npm test`/`./gradlew test`만으로는
  CI에서 깨지는 문제를 잡지 못한다.
- Checkstyle 경고를 남긴 채 커밋하지 마라. `maxWarnings = 0`이므로 경고는 곧 실패다.
- pre-commit 훅(husky + lint-staged, BE Checkstyle)을 `--no-verify`로 우회하지 마라.
- `.editorconfig`를 어기지 마라: UTF-8, LF, 파일 끝 개행, 후행 공백 제거, 기본 들여쓰기
  2칸(`*.java`/`*.gradle` 4칸, `Makefile` 탭).
- Trivy 이미지 스캔(CRITICAL/HIGH, `exit-code: 1`)을 우회하거나 무시 목록으로 덮지 마라.
  취약점은 의존성 버전을 올려 해결한다.
- `infra/` YAML을 `yamllint`(`.yamllint.yml`) 없이 커밋하지 마라. Helm chart는
  `helm lint`와 `helm template | kubeconform -strict`까지 통과시킨다.
- Terraform 코드를 `terraform fmt -check -recursive` 없이 커밋하지 마라.

**근거**: CI가 잡는 실패를 로컬에서 미리 잡는 비용이 훨씬 싸다. 게이트 우회는 파이프라인을
망가뜨린 채 다른 사람의 PR까지 막는다.

### VII. Git 밖에서 인프라를 바꾸지 마라

- `kubectl apply`나 `helm install`로 클러스터를 직접 변경하지 마라. 모든 설치·변경은
  Git 커밋 → ArgoCD(App of Apps) 동기화로만 이루어진다.
- `terraform apply`를 로컬에서 수동 실행하지 마라. PR에서 plan, `main` 머지 시 GitHub Actions
  OIDC로 자동 apply된다. 정적 AWS 키를 만들지 마라.
- ArgoCD `Application`에 상세 설정을 직접 쓰지 마라. Application은 `infra/helm/<app>/` 디렉터리를
  가리키는 역할만 한다. 설정은 wrapper chart의 `Chart.yaml`(dependencies) + `values.yaml`에 둔다.
- 렌더링을 확인하지 않은 Helm 변경을 커밋하지 마라. `helm template`으로 결과를 눈으로 확인한다.
  값이 Helm `tpl`을 거쳐 평가되는 설정(예: Alloy River `{{ }}`)은 특히 실증 검증이 필요하다.
- 노드(`note1`, `note2`)에 허락 없이 패키지를 설치하거나 설정을 바꾸지 마라. 노드 상태는
  Ansible(`infra/ansible/`)로 코드화하고, 임시방편 대신 정석으로 고친다.
- 컨테이너 안에서 런타임에 패키지를 설치하지 마라(`apt`, `microdnf`, `awscli` 등). 공식
  prebuilt 이미지를 조합한다.
- 비밀값을 평문으로 커밋하지 마라. SealedSecret을 사용하고 원본은 추적 제외된 `.secrets`에
  기록한다. `.secrets` 갱신을 빠뜨린 채 새 SealedSecret을 추가하지 마라.
- 서비스 비밀번호를 임의로 리셋·변경하지 마라. 반드시 사용자 확인을 받고 `.secrets`를 참조한다.

**근거**: 클러스터의 진짜 상태가 Git이 아닌 곳에 생기는 순간 GitOps는 무너지고, 드리프트는
장애가 나서야 발견된다.

## 기술 스택 및 구조 제약

- **BE**: Spring Boot 4.0.7 / Java 25, Gradle Groovy DSL, 멀티모듈. 다른 빌드 도구나 언어
  레벨로 갈아타지 마라.
- **BE JSON**: `ObjectMapper`를 `com.fasterxml.jackson`에서 import하지 마라. Boot 4는 Jackson 3
  (`tools.jackson`)을 쓴다. 애너테이션만 `com.fasterxml` 네임스페이스를 유지한다.
- **BE 스키마**: Liquibase에서 boolean 컬럼을 `BOOLEAN`(tinyint)으로 선언하지 마라. Hibernate
  validate가 실패한다. `BIT(1)`을 쓴다.
- **BE 응답**: 성공/에러 응답을 임의 형태로 반환하지 마라. 성공은 `CustomResponseCode`, 에러는
  `ErrorCode`(`GLB-ERR-XXX`) enum에 정의하고 `CustomException`을 던져
  `GlobalExceptionHandler`가 처리하게 한다.
- **BE 프로파일**: `local`/`prod`/`test` 외의 프로파일을 늘리지 마라. `prod` 접속 정보를
  코드나 설정 파일에 하드코딩하지 말고 환경변수로 주입한다.
- **FE**: React + TypeScript + Vite, Node 22. 테스트는 Vitest + RTL + MSW, E2E는 Playwright.
  대체 러너를 도입하지 마라.
- **FE 검증**: ProseMirror/드래그 같은 브라우저 상호작용은 단위 테스트만으로 통과 처리하지
  마라. Playwright 실제 입력으로 실증한다.
- **Infra**: ArgoCD + Helm wrapper chart + Terraform + Ansible. upstream chart를 fork해
  저장소에 복사하지 마라. `dependencies`로 참조한다.
- 위 스택을 바꾸는 결정은 이 문서의 개정 절차를 거친다.

## 개발 워크플로 및 품질 게이트

1. **이슈 먼저** — 이슈 없이 코드부터 쓰지 마라. 템플릿에 맞춰 이슈를 만들고 assignee는 `wcorn`.
2. **브랜치 생성** — `main` pull 후 새 브랜치. 세션별 고유 워크트리.
3. **구현 + 테스트 동시** — 테스트를 "나중에"로 미루지 마라.
4. **로컬 게이트** — FE `format:check` + `lint` + `test` + `build`, BE `./gradlew check` +
   `build`, Infra `yamllint` + `helm lint` + `helm template | kubeconform` +
   `terraform fmt -check`. 로컬 실동작 확인까지 끝낸 뒤 push.
5. **PR** — base는 항상 `main`, 본문에 `closes #<번호>`. 커밋이 추가되면 본문도 갱신.
6. **CI** — 경로별 파이프라인(BE CI: Gradle build + Trivy, FE CI: format/lint/test/build,
   Infra CI: yamllint/helm lint/kubeconform, Terraform: fmt + plan, Ansible: syntax-check +
   ansible-lint)이 모두 초록일 때만 리뷰 요청. 실패를 재실행으로 넘기지 마라.
7. **머지** — 사람이 판단한다. `main` 머지 시 CD(GHCR push, Terraform apply, Ansible apply)가
   자동 실행된다는 점을 전제로 리뷰한다.
8. **기록** — 문제 해결형 작업이면 Engineering Log, 설계 변경이면 Wiki를 같은 흐름에서 갱신.

## Governance

- 이 문서는 저장소의 다른 관행보다 우선한다. `CLAUDE.md`를 포함한 하위 지침이 본 문서와
  충돌하면 본 문서가 이긴다.
- 원칙을 어기는 변경을 "이번만"으로 통과시키지 마라. 예외가 필요하면 먼저 이 문서를 개정한다.
- 개정 절차: 이슈 생성 → 변경 근거와 영향 범위 명시 → PR로 `.specify/memory/constitution.md`
  수정 → `wcorn` 승인 → 머지. 원칙 변경이 기존 코드에 영향을 주면 마이그레이션 계획을 PR 본문에
  포함한다.
- 버전 정책(semver):
  - **MAJOR**: 원칙 제거 또는 하위 호환되지 않는 재정의.
  - **MINOR**: 원칙·섹션 추가 또는 실질적 확장.
  - **PATCH**: 문구 정리, 오타, 의미가 바뀌지 않는 보완.
- 준수 확인: 모든 PR 리뷰는 위 원칙 위반 여부를 함께 확인한다. 위반이 발견되면 승인하지 마라.
- 런타임 개발 지침은 `CLAUDE.md`를 참조한다. `CLAUDE.md`는 본 문서의 구체적 실행 안내서이며,
  본 문서가 개정되면 함께 갱신한다.

**Version**: 1.0.0 | **Ratified**: 2026-08-07 | **Last Amended**: 2026-08-07
