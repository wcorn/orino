package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 열 추가 요청. key는 서버가 발급하므로 label만 받는다. */
public record AddColumnRequest(
        @NotBlank(message = "label은 필수입니다.")
        @Size(max = 255, message = "label은 255자 이하여야 합니다.")
        String label
) {
}
