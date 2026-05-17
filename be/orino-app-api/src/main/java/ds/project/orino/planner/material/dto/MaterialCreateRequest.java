package ds.project.orino.planner.material.dto;

import ds.project.orino.domain.planner.material.entity.MaterialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MaterialCreateRequest(
        @NotBlank(message = "title은 비어 있을 수 없습니다.")
        @Size(min = 1, max = 200, message = "title은 1~200자여야 합니다.")
        String title,

        @NotNull(message = "type은 필수입니다.")
        MaterialType type
) {
}
