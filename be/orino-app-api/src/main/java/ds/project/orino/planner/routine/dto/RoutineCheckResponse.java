package ds.project.orino.planner.routine.dto;

import ds.project.orino.domain.routine.entity.RoutineCheck;

import java.time.LocalDate;

public record RoutineCheckResponse(
        Long id,
        Long routineId,
        LocalDate checkDate,
        boolean completed
) {

    public static RoutineCheckResponse from(RoutineCheck check) {
        return new RoutineCheckResponse(
                check.getId(),
                check.getRoutine().getId(),
                check.getCheckDate(),
                check.isCompleted()
        );
    }
}
