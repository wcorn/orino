package ds.project.orino.planner.routine.dto;

import ds.project.orino.domain.routine.entity.RoutineException;

import java.time.LocalDate;

public record RoutineExceptionResponse(
        Long id,
        Long routineId,
        LocalDate exceptionDate
) {

    public static RoutineExceptionResponse from(RoutineException exception) {
        return new RoutineExceptionResponse(
                exception.getId(),
                exception.getRoutine().getId(),
                exception.getExceptionDate()
        );
    }
}
