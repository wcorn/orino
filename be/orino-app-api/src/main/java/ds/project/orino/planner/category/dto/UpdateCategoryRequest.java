package ds.project.orino.planner.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
        @Size(max = 50) String icon,
        int sortOrder
) {
}
