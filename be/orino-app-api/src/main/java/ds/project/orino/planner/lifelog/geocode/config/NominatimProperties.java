package ds.project.orino.planner.lifelog.geocode.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Nominatim(OSM) 지오코딩 설정.
 *
 * <p>Nominatim 사용 정책 준수를 위한 값들: 식별 가능한 {@code userAgent}(필수), 초당 1회 이하로
 * 제한하는 {@code minInterval}, 결과 캐시 TTL. 공개 인스턴스는 단일 사용자·저볼륨이라 충분하다.
 *
 * @param baseUrl        Nominatim base URL
 * @param userAgent      식별용 User-Agent (Nominatim 정책상 필수)
 * @param acceptLanguage 응답 언어 (예: ko)
 * @param minInterval    연속 호출 최소 간격(rate limit)
 * @param reverseTtl     역지오코딩 캐시 TTL
 * @param searchTtl      정지오코딩(검색) 캐시 TTL
 * @param connectTimeout 연결 타임아웃
 * @param readTimeout    읽기 타임아웃
 */
@ConfigurationProperties(prefix = "geocoding.nominatim")
public record NominatimProperties(
        String baseUrl,
        String userAgent,
        String acceptLanguage,
        Duration minInterval,
        Duration reverseTtl,
        Duration searchTtl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
