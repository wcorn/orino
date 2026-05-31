package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.planner.flashcard.entity.Flashcard;

public record CalendarReviewFlashcard(
        Long id,
        String front,
        CalendarReviewMaterial material
) {
    public static CalendarReviewFlashcard of(Flashcard f, CalendarReviewMaterial material) {
        return new CalendarReviewFlashcard(f.getId(), f.getFront(), material);
    }
}
