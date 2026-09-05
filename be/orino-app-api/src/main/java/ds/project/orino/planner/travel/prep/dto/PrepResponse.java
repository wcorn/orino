package ds.project.orino.planner.travel.prep.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 준비 화면 한 벌(API §10). 한 번에 다 내린다 — 여행 하나에 항목은 수십 개고,
 * 분류별로 나눠 부르면 진행률과 목록이 서로 다른 순간의 값을 보게 된다.
 *
 * @param dday 출발까지 남은 일수. 첫날 기준 도시의 오늘로 센다
 */
public record PrepResponse(
        Long tripId,
        LocalDate startDate,
        long dday,
        int total,
        int done,
        int overdueCount,
        List<PrepGroup> groups
) {
}
