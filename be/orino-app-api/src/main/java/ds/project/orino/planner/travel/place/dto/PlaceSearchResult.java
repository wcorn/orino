package ds.project.orino.planner.travel.place.dto;

import java.math.BigDecimal;

/**
 * S-06 장소 검색 결과 한 건.
 *
 * @param id       이미 담아 둔 장소면 내부 id, 아니면 null
 * @param photoUrl MinIO에 캐시한 대표 사진. 사진 캐시는 별도 이슈라 지금은 항상 null
 * @param loved    이전 여행에서 평점 4 이상을 준 곳(⭐ 좋았던 곳).
 *                 기록 테이블이 4단계라 지금은 항상 false
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
        BigDecimal lng,
        boolean loved
) {
}
