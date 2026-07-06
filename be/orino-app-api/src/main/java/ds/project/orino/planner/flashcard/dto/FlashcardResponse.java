package ds.project.orino.planner.flashcard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.entity.FlashcardType;
import ds.project.orino.planner.review.dto.ReviewScheduleView;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlashcardResponse(
        Long id,
        Long materialId,
        FlashcardType type,
        String front,
        String back,
        List<OrderingItem> items,
        ReviewScheduleView nextReview,
        Instant createdAt
) {
    public static FlashcardResponse of(Flashcard f, List<OrderingItem> items, ReviewScheduleView nextReview) {
        return new FlashcardResponse(
                f.getId(), f.getMaterialId(), f.getType(), f.getFront(), f.getBack(),
                items, nextReview, f.getCreatedAt());
    }

    public static FlashcardResponse withoutReview(Flashcard f, List<OrderingItem> items) {
        return of(f, items, null);
    }
}
