package ds.project.orino.planner.travel.place.dto;

import java.math.BigDecimal;

/**
 * S-06 장소 검색 결과 한 건.
 *
 * @param id       이미 담아 둔 장소면 내부 id, 아니면 null
 * @param photoUrl <b>이미 담아 둔 장소</b>의 캐시된 대표 사진. 아직 담지 않은 곳은 null이다 —
 *                 검색 결과 20개의 사진을 여기서 받으면 화면 한 번에 유료 호출 20번이다
 * @param photoAttribution 사진 저작자. 사진을 <b>보여주는 곳마다</b> 함께 표시해야 한다(구글 약관)
 * @param loved    이전 여행에서 평점 4 이상을 준 곳(⭐ 좋았던 곳). 판정은 별도 이슈라 지금은 false
 */
public record PlaceSearchResult(
        Long id,
        String googlePlaceId,
        String name,
        String category,
        String address,
        BigDecimal rating,
        String photoUrl,
        String photoAttribution,
        BigDecimal lat,
        BigDecimal lng,
        boolean loved
) {
}
