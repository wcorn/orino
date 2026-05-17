package ds.project.orino.planner.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewScheduleView(
        Long id,
        Long flashcardId,
        Integer sequence,
        LocalDate scheduledDate,
        Integer intervalDays,
        BigDecimal easeFactor,
        ReviewStatus status
) {
    public static ReviewScheduleView nextReview(ReviewSchedule r) {
        return new ReviewScheduleView(
                r.getId(), null, r.getSequence(),
                r.getScheduledDate(), r.getIntervalDays(), r.getEaseFactor(),
                null);
    }

    public static ReviewScheduleView firstReview(ReviewSchedule r) {
        return new ReviewScheduleView(
                r.getId(), r.getFlashcardId(), r.getSequence(),
                r.getScheduledDate(), r.getIntervalDays(), r.getEaseFactor(),
                r.getStatus());
    }
}
