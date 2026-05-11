package ds.project.orino.planner.material.dto;

import ds.project.orino.domain.planner.material.entity.MaterialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MaterialCreateRequest(
        @NotBlank(message = "제목을 입력해주세요.")
        @Size(min = 1, max = 200, message = "제목은 1~200자여야 합니다.")
        String title,

        @NotNull(message = "자료 타입을 입력해주세요.")
        MaterialType type
) {
}
