package ds.project.orino.planner.flashcard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.planner.review.dto.ReviewScheduleView;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlashcardResponse(
        Long id,
        Long materialId,
        String front,
        String back,
        ReviewScheduleView nextReview,
        Instant createdAt
) {
    public static FlashcardResponse of(Flashcard f, ReviewScheduleView nextReview) {
        return new FlashcardResponse(
                f.getId(), f.getMaterialId(), f.getFront(), f.getBack(),
                nextReview, f.getCreatedAt());
    }

    public static FlashcardResponse withoutReview(Flashcard f) {
        return new FlashcardResponse(
                f.getId(), f.getMaterialId(), f.getFront(), f.getBack(),
                null, f.getCreatedAt());
    }
}
