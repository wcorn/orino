package ds.project.orino.planner.travel.route.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Google Routes 설정. API 키는 Places와 같은 것을 쓴다(같은 GCP 프로젝트의 제한 키).
 *
 * <p>{@code apiKey}가 비면 이동시간만 직선거리로 대체되고 보드는 그대로 뜬다 —
 * 이동시간 때문에 일정이 안 보이면 안 된다.
 *
 * @param apiKey         Places/Routes 공용 API 키(비면 fallback으로만 동작)
 * @param baseUrl        Routes API base URL
 * @param languageCode   응답 언어
 * @param cacheTtl       구간 캐시 TTL. 같은 두 지점 사이 거리는 잘 변하지 않는다
 * @param walkThresholdM 이 직선거리 이하면 도보, 초과면 자동차(§1.3 — 1.5km)
 * @param connectTimeout 연결 타임아웃
 * @param readTimeout    읽기 타임아웃
 */
@ConfigurationProperties(prefix = "travel.routes")
public record RoutesProperties(
        String apiKey,
        String baseUrl,
        String languageCode,
        Duration cacheTtl,
        int walkThresholdM,
        Duration connectTimeout,
        Duration readTimeout
) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
