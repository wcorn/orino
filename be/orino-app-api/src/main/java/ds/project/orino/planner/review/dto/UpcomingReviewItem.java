package ds.project.orino.planner.review.dto;

import java.time.Instant;

public record UpcomingReviewItem(
        Long id,
        Instant scheduledAt,
        WhenKind whenKind,
        boolean overdue,
        CardType cardType,
        ReviewCardView flashcard
) {
}
