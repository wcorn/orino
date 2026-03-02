# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Structure

```
orino/
├── be/      # Spring Boot 백엔드
├── fe/      # React 프론트엔드 (스펙 미정)
└── infra/   # Kubernetes GitOps
```

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

## Commit Message Format

`.gitmessage.txt` 기준: `<type>: <subject>` (subject ≤ 50자)
Types: `feat`, `fix`, `docs`, `test`, `refactor`, `style`, `chore`
