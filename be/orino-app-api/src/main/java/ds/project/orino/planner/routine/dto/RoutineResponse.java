package ds.project.orino.planner.routine.dto;

import ds.project.orino.domain.fixedschedule.entity.RecurrenceType;
import ds.project.orino.domain.routine.entity.Routine;
import ds.project.orino.domain.routine.entity.RoutineStatus;

public record RoutineResponse(
        Long id,
        String title,
        Long categoryId,
        int durationMinutes,
        RecurrenceType recurrenceType,
        String recurrenceDays,
        RoutineStatus status,
        StreakInfo streak
) {

    public static RoutineResponse from(Routine routine, StreakInfo streak) {
        return new RoutineResponse(
                routine.getId(),
                routine.getTitle(),
                routine.getCategory() != null
                        ? routine.getCategory().getId() : null,
                routine.getDurationMinutes(),
                routine.getRecurrenceType(),
                routine.getRecurrenceDays(),
                routine.getStatus(),
                streak
        );
    }
}
