package ds.project.orino.planner.material.dto;

import ds.project.orino.domain.material.entity.UnitDifficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUnitRequest(
        @NotBlank @Size(max = 200) String title,
        Integer estimatedMinutes,
        UnitDifficulty difficulty,
        int sortOrder
) {
}
