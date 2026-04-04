package ds.project.orino.planner.material.dto;

import jakarta.validation.constraints.Positive;

public record DailyOverrideRequest(
        @Positive int minutes
) {
}
