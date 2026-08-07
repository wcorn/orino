package ds.project.orino.planner.travel.place.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Google Places 설정.
 *
 * <p>{@code apiKey}가 비어 있으면 장소 기능만 꺼지고 앱은 정상 기동한다 — 2단계 전 환경이나
 * 로컬에서 키 없이 나머지를 개발할 수 있어야 한다.
 *
 * @param apiKey         Places/Routes 공용 API 키(비면 기능 비활성)
 * @param baseUrl        Places API (New) base URL
 * @param languageCode   응답 언어
 * @param searchTtl      검색 결과 캐시 TTL(§4.7 — 1시간)
 * @param detailsTtl     장소 상세 유효기간(§4.7 — 30일). 지나면 재조회
 * @param maxResults     검색 결과 개수(§S-06 — 20개)
 * @param searchRadiusM  목적지 좌표를 중심으로 검색을 편향시킬 반경
 * @param connectTimeout 연결 타임아웃
 * @param readTimeout    읽기 타임아웃
 */
@ConfigurationProperties(prefix = "travel.places")
public record PlacesProperties(
        String apiKey,
        String baseUrl,
        String languageCode,
        Duration searchTtl,
        Duration detailsTtl,
        int maxResults,
        int searchRadiusM,
        Duration connectTimeout,
        Duration readTimeout
) {

    /** 키가 없으면 외부 호출을 시도하지 않는다. */
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
