package ds.project.orino.planner.travel.place.dto;

import java.math.BigDecimal;

/**
 * S-06 장소 검색 결과 한 건.
 *
 * @param id       이미 담아 둔 장소면 내부 id, 아니면 null
 */
public record PlaceSearchResult(
        Long id,
        String googlePlaceId,
        String name,
        String category,
        String address,
        BigDecimal rating,
        BigDecimal lat,
        BigDecimal lng
) {
}
