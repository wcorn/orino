package ds.project.orino.planner.travel.place.client;

import java.math.BigDecimal;
import java.util.List;

/**
 * Places 응답에서 우리가 쓰는 것만 뽑은 결과.
 *
 * <p>구글 응답 구조를 그대로 위로 흘리지 않는다 — 필드마스크를 바꾸거나 API 버전이 올라가도
 * 서비스·컨트롤러가 흔들리지 않게 여기서 한 번 끊는다.
 *
 * @param googlePlaceId 구글 장소 식별자
 * @param timezone      IANA 타임존. <b>Places가 직접 준다</b>(좌표→타임존 매핑이 필요 없다)
 * @param cityName      이 장소가 속한 도시 이름. 도시 이탈 표시(§1124)가 쓴다
 * @param countryCode   ISO 3166-1 alpha-2. 통화를 여기서 유도한다
 * @param openingHours  영업시간 원본 JSON. 구조를 해석하지 않고 그대로 캐시해 FE에 넘긴다
 * @param types         Google place type 목록. 목적지 검색에서 행정구역만 골라내는 데 쓴다
 */
public record PlaceResult(
        String googlePlaceId,
        String name,
        String address,
        BigDecimal lat,
        BigDecimal lng,
        String category,
        BigDecimal rating,
        String phone,
        String openingHours,
        String timezone,
        String cityName,
        String countryCode,
        List<String> types
) {

    /** {@code types}는 검색 종류에 따라 요청하지 않으므로 null이 올 수 있다. */
    public PlaceResult {
        types = types == null ? List.of() : List.copyOf(types);
    }
}
