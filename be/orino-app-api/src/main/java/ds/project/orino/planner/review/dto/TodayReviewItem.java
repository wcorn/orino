package ds.project.orino.planner.review.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TodayReviewItem(
        Long id,
        LocalDateTime scheduledAt,
        int delayDays,
        int sequence,
        int intervalDays,
        BigDecimal easeFactor,
        TodayReviewFlashcard flashcard,
        PreviewView preview
) {
}
