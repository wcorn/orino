package ds.project.orino.planner.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.entity.FlashcardType;
import ds.project.orino.planner.flashcard.dto.OrderingItem;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TodayReviewFlashcard(
        Long id,
        FlashcardType type,
        String front,
        String back,
        List<OrderingItem> items,
        TodayReviewMaterial material
) {
    public static TodayReviewFlashcard of(Flashcard f, List<OrderingItem> items, TodayReviewMaterial material) {
        return new TodayReviewFlashcard(f.getId(), f.getType(), f.getFront(), f.getBack(), items, material);
    }
}
