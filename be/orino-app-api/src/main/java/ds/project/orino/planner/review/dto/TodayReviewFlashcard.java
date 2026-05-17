package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.planner.flashcard.entity.Flashcard;

public record TodayReviewFlashcard(
        Long id,
        String front,
        String back,
        TodayReviewMaterial material
) {
    public static TodayReviewFlashcard of(Flashcard f, TodayReviewMaterial material) {
        return new TodayReviewFlashcard(f.getId(), f.getFront(), f.getBack(), material);
    }
}
