package ds.project.orino.planner.flashcard.dto;

import jakarta.validation.constraints.Size;

public record FlashcardUpdateRequest(
        @Size(min = 1, max = 1000, message = "front는 1~1000자여야 합니다.")
        String front,

        @Size(min = 1, max = 1000, message = "back은 1~1000자여야 합니다.")
        String back
) {
}
