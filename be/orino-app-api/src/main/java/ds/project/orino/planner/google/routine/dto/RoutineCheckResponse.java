package ds.project.orino.planner.google.routine.dto;

import java.time.LocalDate;

/**
 * 습관 완료 체크 토글 결과.
 */
public record RoutineCheckResponse(
        String recurringEventId,
        LocalDate date,
        boolean done
) {
}
