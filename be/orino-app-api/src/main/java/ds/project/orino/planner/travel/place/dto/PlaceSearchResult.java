package ds.project.orino.planner.travel.place.dto;

import java.math.BigDecimal;

/**
 * S-06 장소 검색 결과 한 건.
 *
 * @param id       이미 담아 둔 장소면 내부 id, 아니면 null
 * @param photoUrl MinIO에 캐시한 대표 사진. 장소 사진은 미도입이라 지금은 항상 null(결정 기록 D-16)
 */
public record PlaceSearchResult(
        Long id,
        String googlePlaceId,
        String name,
        String category,
        String address,
        BigDecimal rating,
        String photoUrl,
        BigDecimal lat,
        BigDecimal lng
) {
}
