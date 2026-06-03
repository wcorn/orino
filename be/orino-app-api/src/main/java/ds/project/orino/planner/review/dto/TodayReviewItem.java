package ds.project.orino.planner.review.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TodayReviewItem(
        Long id,
        Instant scheduledAt,
        int delayDays,
        int sequence,
        int intervalDays,
        BigDecimal easeFactor,
        TodayReviewFlashcard flashcard,
        PreviewView preview
) {
}
