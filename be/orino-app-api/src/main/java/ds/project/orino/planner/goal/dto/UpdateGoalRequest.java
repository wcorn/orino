package ds.project.orino.planner.goal.dto;

import ds.project.orino.domain.goal.entity.PeriodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateGoalRequest(
        @NotBlank @Size(max = 100) String title,
        String description,
        Long categoryId,
        @NotNull PeriodType periodType,
        @NotNull LocalDate startDate,
        LocalDate deadline
) {
}
