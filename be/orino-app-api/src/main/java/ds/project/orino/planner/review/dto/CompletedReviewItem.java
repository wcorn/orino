package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.planner.review.entity.Rating;

import java.time.Instant;

public record CompletedReviewItem(
        Long id,
        Instant completedAt,
        Rating rating,
        int sequence,
        CardType cardType,
        ReviewCardView flashcard
) {
}
