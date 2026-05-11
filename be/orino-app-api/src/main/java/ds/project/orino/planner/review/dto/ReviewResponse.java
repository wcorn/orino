package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long studyUnitId,
        Integer sequence,
        LocalDate scheduledDate,
        Integer intervalDays,
        BigDecimal easeFactor,
        ReviewStatus status,
        LocalDateTime completedAt
) {

    public static ReviewResponse from(ReviewSchedule review) {
        return new ReviewResponse(
                review.getId(),
                review.getStudyUnitId(),
                review.getSequence(),
                review.getScheduledDate(),
                review.getIntervalDays(),
                review.getEaseFactor(),
                review.getStatus(),
                review.getCompletedAt()
        );
    }
}
