package ds.project.orino.planner.google.calendar.dto;

/**
 * 통합 피드의 할 일(Google Tasks) 항목. M3(#484)에서 합류하며, 그 전까지는 항상 빈 목록이다.
 */
public record PlannerTask(
        String id,
        String title,
        String due,
        boolean completed,
        String source
) {
}
