# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# 1) 빌드 스크립트/래퍼만 먼저 복사 → 의존성 해석 레이어를 캐시한다.
#    소스 코드만 바뀌면 이 레이어(의존성 다운로드)는 gha 캐시에서 재사용되어
#    매 빌드마다 전체 의존성을 다시 받지 않는다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY gradle.properties ./
COPY orino-common/build.gradle ./orino-common/
COPY orino-domain-rdb/build.gradle ./orino-domain-rdb/
COPY orino-domain-redis/build.gradle ./orino-domain-redis/
COPY orino-core-web/build.gradle ./orino-core-web/
COPY orino-app-api/build.gradle ./orino-app-api/
RUN ./gradlew :orino-app-api:dependencies --no-daemon --quiet || true

# 2) 소스 복사 후 빌드 (소스만 변경 시 위 의존성 레이어는 캐시 히트)
COPY . .
RUN ./gradlew :orino-app-api:build -x test --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /app/orino-app-api/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
