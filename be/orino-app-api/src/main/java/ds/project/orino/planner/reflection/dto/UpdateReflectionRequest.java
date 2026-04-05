package ds.project.orino.planner.reflection.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateReflectionRequest(
        @NotNull @Min(1) @Max(5) Integer mood,
        @Size(max = 2000) String memo) {
}
