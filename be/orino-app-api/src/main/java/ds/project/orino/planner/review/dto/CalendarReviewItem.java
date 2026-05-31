package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.planner.review.entity.Rating;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;

import java.time.LocalDate;

public record CalendarReviewItem(
        Long id,
        LocalDate scheduledDate,
        ReviewStatus status,
        Rating rating,
        int sequence,
        CalendarReviewFlashcard flashcard
) {
}
