package ds.project.orino.planner.travel.tools.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 도구(날씨·환율) 설정.
 *
 * <p>둘 다 무료·무인증이라 키가 없다. 그래도 캐시를 두는 이유는 §4.7 —
 * 남의 무료 서비스를 필요 이상으로 두드리지 않는 것도 예의다.
 *
 * @param weatherTtl 예보 캐시(§4.7 — 6시간). 예보 자체가 그보다 자주 안 바뀐다
 * @param fxTtl      환율 캐시(§4.7 — 24시간). ECB는 하루 한 번 고시한다
 */
@ConfigurationProperties(prefix = "travel.tools")
public record ToolsProperties(
        String weatherBaseUrl,
        String fxUrl,
        Duration weatherTtl,
        Duration fxTtl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
