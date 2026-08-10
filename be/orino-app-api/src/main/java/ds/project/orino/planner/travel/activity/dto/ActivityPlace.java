package ds.project.orino.planner.travel.activity.dto;

import ds.project.orino.domain.planner.travel.entity.TravelPlace;

import java.math.BigDecimal;

/**
 * 일정에 붙은 장소 요약. 좌표는 지도·이동시간이 쓴다.
 *
 * <p><b>도시 정보를 함께 싣는다</b>(v2.1). 그날 기준 도시와 같은 도시인지 판정하려면 화면이
 * 아니라 데이터가 답을 갖고 있어야 한다.
 *
 * @param cityName     이 장소가 속한 도시 표시명. 화면의 {@code · 오사카} 꼬리표
 * @param cityPlaceRef 도시 식별자. <b>도시 일치 판정은 이 값으로만 한다</b> — 좌표 거리로
 *                     추측하면 오사카-교토(43km)와 도쿄-요코하마(30km)에서 답이 갈린다(D-23)
 */
public record ActivityPlace(
        Long id,
        String name,
        String address,
        BigDecimal lat,
        BigDecimal lng,
        String cityName,
        String cityPlaceRef
) {

    public static ActivityPlace from(TravelPlace place) {
        return new ActivityPlace(place.getId(), place.getName(), place.getAddress(),
                place.getLat(), place.getLng(), place.getCityName(), place.getCityPlaceRef());
    }
}
