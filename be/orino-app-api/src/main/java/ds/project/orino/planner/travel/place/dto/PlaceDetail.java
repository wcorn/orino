package ds.project.orino.planner.travel.place.dto;

import ds.project.orino.domain.planner.travel.entity.TravelPlace;

import java.math.BigDecimal;

/**
 * 저장된 장소 상세. 일정 상세 화면의 장소 블록이 쓴다.
 *
 * @param openingHours 구글 영업시간 원본 JSON. 서버가 구조를 해석하지 않고 그대로 넘긴다 —
 *                     표기 규칙이 나라마다 달라 서버가 문자열을 만들면 오히려 어색해진다
 * @param photoUrl     MinIO에 캐시한 대표 사진. 없으면 null
 * @param photoAttribution 사진 저작자. <b>사진을 보여줄 때 함께 표시해야 한다</b>(구글 약관)
 */
public record PlaceDetail(
        Long id,
        String googlePlaceId,
        String name,
        String address,
        BigDecimal lat,
        BigDecimal lng,
        String category,
        String phone,
        BigDecimal rating,
        String openingHours,
        String photoUrl,
        String photoAttribution,
        boolean manualEntry
) {

    public static PlaceDetail from(TravelPlace place, String photoUrl) {
        return new PlaceDetail(place.getId(), place.getGooglePlaceId(), place.getName(),
                place.getAddress(), place.getLat(), place.getLng(), place.getCategory(),
                place.getPhone(), place.getRating(), place.getOpeningHours(), photoUrl,
                place.getPhotoAttribution(), place.isManualEntry());
    }
}
