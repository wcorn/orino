package ds.project.orino.planner.google.calendar.dto;

/**
 * 통합 피드/Tasks 응답의 할 일(Google Tasks) 항목.
 *
 * @param due 마감일(날짜 "2026-06-12") 또는 null
 */
public record PlannerTask(
        String id,
        String title,
        String due,
        boolean completed,
        String notes,
        String source
) {
}
