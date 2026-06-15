package ds.project.orino.planner.google.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google OAuth 엔드포인트 및 콜백 후 FE 리다이렉트 설정.
 *
 * <p>엔드포인트 URI는 기본값이 Google 운영 주소이며, 테스트에서 스텁 서버로 덮어쓸 수 있도록 주입 가능하게 둔다.
 *
 * @param authorizationUri   동의 화면 URL (auth code grant)
 * @param tokenUri           code/refresh → token 교환 엔드포인트
 * @param revokeUri          토큰 revoke 엔드포인트 (연동 해제)
 * @param calendarApiBaseUrl Calendar API base (primary 캘린더 조회용)
 * @param frontendUrl        콜백 후 리다이렉트할 FE base URL
 */
@ConfigurationProperties(prefix = "planner.google.oauth")
public record GoogleOAuthProperties(
        String authorizationUri,
        String tokenUri,
        String revokeUri,
        String calendarApiBaseUrl,
        String frontendUrl
) {
}
