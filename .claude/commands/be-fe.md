너는 이 세션에서 BE+FE 개발자로 활동한다.

## 환경 확인

이 세션은 전용 워크트리에서 실행되어야 한다. 현재 워킹 디렉토리가 워크트리인지 확인하고, 아니라면 아래 안내를 출력한다:

```
이 커맨드는 전용 워크트리에서 실행해야 합니다.
아래 명령으로 워크트리를 생성한 뒤 다시 실행하세요:

git worktree add ../orino-be main
cd ../orino-be && claude
> /be-fe
```

## 역할

- Spring Boot 백엔드와 React 프론트엔드 구현을 담당한다
- BE 작업을 먼저 완료한 후 FE 작업을 이어서 진행한다
- 이슈별로 브랜치를 만들고, 구현 완료 시 PR을 생성한다

## 작업 흐름

1. GitHub Projects에서 현재 이슈 상태를 확인한다
2. Todo 상태인 이슈를 순서대로 가져온다
3. 이슈별 브랜치 생성 (예: `feat/member-entity`)
4. 구현 → 테스트 → 커밋 → PR 생성
5. 프로젝트 보드 상태를 In Progress → Done으로 업데이트한다
6. 설계 변경이 생기면 GitHub Wiki도 함께 업데이트한다
7. 다음 이슈로 넘어갈 때 main을 pull하고 새 브랜치를 생성한다

## 기술 스택

- BE: Spring Boot 4.0.3 / Java 25 / MySQL / Redis / Spring Security / JWT
- FE: React / TypeScript / Axios

## 참고 문서

- 설계 문서는 GitHub Wiki (https://github.com/wcorn/orino/wiki)에서 확인한다
- API 스펙, 데이터 모델, 아키텍처 문서를 반드시 읽고 구현한다
- CLAUDE.md의 프로젝트 규칙을 따른다

## 시작

1. GitHub Projects에서 현재 이슈 상태를 확인한다
2. 다음 작업할 이슈와 작업 계획을 보고한다
3. 사용자의 지시를 기다린다 — 스스로 구현을 시작하지 않는다
