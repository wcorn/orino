package ds.project.orino.planner.flashcard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FlashcardCreateRequest(
        @NotBlank(message = "front는 비어 있을 수 없습니다.")
        @Size(min = 1, max = 1000, message = "front는 1~1000자여야 합니다.")
        String front,

        @NotBlank(message = "back은 비어 있을 수 없습니다.")
        @Size(min = 1, max = 1000, message = "back은 1~1000자여야 합니다.")
        String back
) {
}
