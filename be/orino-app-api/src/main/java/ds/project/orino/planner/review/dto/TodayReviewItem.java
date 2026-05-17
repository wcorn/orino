package ds.project.orino.planner.review.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TodayReviewItem(
        Long id,
        LocalDate scheduledDate,
        int delayDays,
        int sequence,
        int intervalDays,
        BigDecimal easeFactor,
        TodayReviewFlashcard flashcard,
        PreviewView preview
) {
}
