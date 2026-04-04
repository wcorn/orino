package ds.project.orino.planner.material.dto;

import jakarta.validation.constraints.Positive;

public record AllocationRequest(
        @Positive int defaultMinutes
) {
}
