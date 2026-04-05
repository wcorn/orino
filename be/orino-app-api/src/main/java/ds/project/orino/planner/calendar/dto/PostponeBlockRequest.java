package ds.project.orino.planner.calendar.dto;

import jakarta.validation.constraints.NotNull;

public record PostponeBlockRequest(
        @NotNull PostponeStrategy strategy) {
}
