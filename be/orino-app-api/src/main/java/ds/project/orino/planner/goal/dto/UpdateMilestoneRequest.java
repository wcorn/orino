package ds.project.orino.planner.goal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateMilestoneRequest(
        @NotBlank @Size(max = 100) String title,
        LocalDate deadline,
        int sortOrder
) {
}
