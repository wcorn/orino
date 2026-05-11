package ds.project.orino.planner.material.dto;

import ds.project.orino.domain.planner.material.entity.MaterialStatus;
import jakarta.validation.constraints.Size;

public record MaterialUpdateRequest(
        @Size(min = 1, max = 200, message = "제목은 1~200자여야 합니다.")
        String title,

        MaterialStatus status
) {
}
