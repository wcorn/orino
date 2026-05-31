package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.planner.review.entity.Rating;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;

import java.time.LocalDateTime;

public record CalendarReviewItem(
        Long id,
        LocalDateTime scheduledAt,
        ReviewStatus status,
        Rating rating,
        int sequence,
        CalendarReviewFlashcard flashcard
) {
}
