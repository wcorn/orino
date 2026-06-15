package ds.project.orino.planner.google.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Google OAuth / API 연동 설정.
 *
 * <p>일정/할 일은 Google이 source of truth이며 BE가 BFF 프록시로 모든 Google API를 호출한다.
 * client-id/secret 은 운영에선 SealedSecret → 환경변수, 로컬에선 {@code .env} 로 주입한다.
 *
 * @param clientId       OAuth 2.0 클라이언트 ID
 * @param clientSecret   OAuth 2.0 클라이언트 secret
 * @param redirectUri    승인된 리디렉션 URI (Google Cloud Console 등록값과 일치해야 함)
 * @param scopes         요청 scope 목록 (calendar + tasks)
 * @param connectTimeout Google API 연결 타임아웃
 * @param readTimeout    Google API 응답 타임아웃
 */
@ConfigurationProperties(prefix = "planner.google")
public record GoogleApiProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        List<String> scopes,
        Duration connectTimeout,
        Duration readTimeout
) {
}
