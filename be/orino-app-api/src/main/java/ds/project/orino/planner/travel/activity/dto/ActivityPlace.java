package ds.project.orino.planner.travel.activity.dto;

import ds.project.orino.domain.planner.travel.entity.TravelPlace;

import java.math.BigDecimal;

/**
 * 일정에 붙은 장소 요약. 좌표는 지도·이동시간(2단계)이 쓴다.
 *
 * <p>1단계에는 장소를 만드는 경로가 없어 항상 {@code null}로 나가지만, 조립은 지금 해 둔다 —
 * 2단계에서 장소가 생기는 순간 보드·상세가 그대로 채워진다.
 */
public record ActivityPlace(
        Long id,
        String name,
        String address,
        BigDecimal lat,
        BigDecimal lng
) {

    public static ActivityPlace from(TravelPlace place) {
        return new ActivityPlace(place.getId(), place.getName(), place.getAddress(),
                place.getLat(), place.getLng());
    }
}
