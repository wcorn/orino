package ds.project.orino.planner.routine.dto;

import ds.project.orino.domain.routine.entity.RoutineStatus;
import jakarta.validation.constraints.NotNull;

public record RoutineStatusRequest(
        @NotNull RoutineStatus status
) {
}
