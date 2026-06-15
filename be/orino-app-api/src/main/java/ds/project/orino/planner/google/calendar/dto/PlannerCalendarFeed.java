package ds.project.orino.planner.google.calendar.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 통합 캘린더 피드 — 일정(Google) + 할 일(Google Tasks) + 복습(orino, 읽기 전용)을 한 번에 반환한다.
 *
 * <p>부분 실패 허용: 한 소스가 실패해도 200으로 나머지를 반환하고 {@code partial=true} + {@code errors[]}.
 * 시간 값은 사용자 시간대(X-Timezone) 기준으로 정규화된다.
 */
public record PlannerCalendarFeed(
        LocalDate from,
        LocalDate to,
        boolean googleConnected,
        boolean partial,
        List<FeedError> errors,
        List<PlannerEvent> events,
        List<PlannerTask> tasks,
        List<PlannerReview> reviews
) {
}
