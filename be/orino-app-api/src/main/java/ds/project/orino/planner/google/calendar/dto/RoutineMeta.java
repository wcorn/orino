package ds.project.orino.planner.google.calendar.dto;

/**
 * 통합 피드 일정에 붙는 루틴 주석. 루틴 인스턴스가 아니면 {@link PlannerEvent#routine()}는 null이다.
 *
 * @param type             "habit" | "schedule"
 * @param recurringEventId 마스터 시리즈 id(인스턴스가 가리키는 시리즈)
 * @param done             habit 완료 여부. R3에서 routine_check 조인 전까지는 false
 */
public record RoutineMeta(
        String type,
        String recurringEventId,
        boolean done
) {
}
