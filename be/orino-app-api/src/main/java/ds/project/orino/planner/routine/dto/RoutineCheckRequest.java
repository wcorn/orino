package ds.project.orino.planner.routine.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RoutineCheckRequest(
        @NotNull LocalDate checkDate
) {
}
