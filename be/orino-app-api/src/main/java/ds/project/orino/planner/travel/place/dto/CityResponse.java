package ds.project.orino.planner.travel.place.dto;

import java.math.BigDecimal;

/**
 * S-03 목적지 후보. <b>타임존·통화를 서버가 확정해서</b> 내려준다.
 *
 * <p>FE는 받아서 보여주기만 한다 — 사용자가 도시를 고르면 여행의 타임존·통화가 자동으로
 * 정해지고, 화면은 "자동 지정됐어요" Alert에 이 값을 그대로 쓴다.
 */
public record CityResponse(
        String googlePlaceId,
        String name,
        String address,
        BigDecimal lat,
        BigDecimal lng,
        String timezone,
        String currency
) {
}
