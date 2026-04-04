package ds.project.orino.planner.material.dto;

import ds.project.orino.domain.material.entity.MissedPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewConfigRequest(
        @NotBlank @Size(max = 50) String intervals,
        @NotNull MissedPolicy missedPolicy
) {
}
