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

**Spring Boot 3.5.6 / Kotlin 1.9.25 / Java 21**, Gradle Kotlin DSL

```bash
cd be
./gradlew build          # Build
./gradlew bootRun        # Run
./gradlew test           # Run all tests
./gradlew clean build    # Clean and rebuild
```

### Profiles

Default active profile: `local` (auto-includes `mysql`, `actuator`, `redis`, `vault`)

| Profile | MySQL | Redis | Vault |
|---------|-------|-------|-------|
| `local` | localhost:3306 (user: dongseok) | localhost:6379 | disabled |
| `dev`   | env vars (MYSQL_HOST, etc.) | env var (REDIS_HOST) | AppRole auth via env vars |
| `test`  | TestContainers (MySQL 8.4.4) | — | disabled |

Vault env vars for dev: `VAULT_ROLE_ID`, `VAULT_SECRET_ID`, `VAULT_HOST`, `VAULT_PORT`

### Response & Error Handling

- **Success codes**: `ResponseCode` enum in `common/response/api/ResponseCode.kt`
- **Error codes**: `ErrorCode` enum in `common/response/exception/ErrorCode.kt` (format: `GLB-ERR-XXX`)
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
