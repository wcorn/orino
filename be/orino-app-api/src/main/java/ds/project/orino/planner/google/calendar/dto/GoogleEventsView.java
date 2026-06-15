package ds.project.orino.planner.google.calendar.dto;

import java.util.List;

/**
 * 일정 조회 결과. 미연동이면 connected=false + 빈 목록. 통합 피드(#479)가 googleConnected로 노출한다.
 */
public record GoogleEventsView(boolean connected, List<PlannerEvent> events) {

    public static GoogleEventsView notConnected() {
        return new GoogleEventsView(false, List.of());
    }

    public static GoogleEventsView connected(List<PlannerEvent> events) {
        return new GoogleEventsView(true, events);
    }
}
