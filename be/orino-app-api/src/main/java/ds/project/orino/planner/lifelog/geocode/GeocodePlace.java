package ds.project.orino.planner.lifelog.geocode;

import java.math.BigDecimal;

/**
 * 지오코딩 결과 한 건. 역/정 지오코딩 응답과 Redis 캐시 직렬화에 공통으로 쓴다.
 *
 * @param placeName 장소명(Nominatim {@code display_name})
 * @param lat       위도
 * @param lng       경도
 */
public record GeocodePlace(
        String placeName,
        BigDecimal lat,
        BigDecimal lng
) {
}
