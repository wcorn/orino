package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameColumnRequest(
        @NotBlank(message = "label은 필수입니다.")
        @Size(max = 255, message = "label은 255자 이하여야 합니다.")
        String label
) {
}
