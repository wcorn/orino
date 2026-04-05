package ds.project.orino.planner.rescheduling.dto;

import jakarta.validation.constraints.NotNull;

public record ApplyRescheduleRequest(
        @NotNull RescheduleStrategy strategy) {
}
