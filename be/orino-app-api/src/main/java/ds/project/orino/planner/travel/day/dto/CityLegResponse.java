package ds.project.orino.planner.travel.day.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 파생된 구간 하나. 구간 편집기(S-03)의 초기값과 지도의 {@code 전체} 모드가 쓴다.
 *
 * <p><b>저장된 것이 아니라 매번 계산한다</b>(D-21). 그래서 날짜를 바꾸면 다음 조회에서
 * 곧바로 반영되고, 구간과 날짜가 어긋나는 상태가 존재하지 않는다.
 *
 * @param legIndex 1부터. 같은 도시를 다시 방문하면 다른 번호다
 * @param days     머무는 일수(당일 포함)
 */
public record CityLegResponse(
        int legIndex,
        Long cityPlaceId,
        String cityName,
        int days,
        LocalDate startDate,
        LocalDate endDate,
        String timezone,
        BigDecimal lat,
        BigDecimal lng
) {
}
