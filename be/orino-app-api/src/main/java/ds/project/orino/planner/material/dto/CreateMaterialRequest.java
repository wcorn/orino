package ds.project.orino.planner.material.dto;

import ds.project.orino.domain.material.entity.DeadlineMode;
import ds.project.orino.domain.material.entity.MaterialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateMaterialRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull MaterialType type,
        Long categoryId,
        Long goalId,
        LocalDate deadline,
        DeadlineMode deadlineMode,
        List<CreateUnitRequest> units
) {
}
