package ds.project.orino.planner.material.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UnitUpdateRequest(
        @Size(min = 1, max = 200, message = "제목은 1~200자여야 합니다.")
        String title,

        @Min(value = 1, message = "sortOrder는 1 이상이어야 합니다.")
        Integer sortOrder
) {
}
