package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.planner.review.entity.Rating;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;

import java.time.LocalDateTime;

public record CompletedReviewView(
        Long id,
        ReviewStatus status,
        Rating rating,
        Integer elapsedDays,
        LocalDateTime completedAt
) {
    public static CompletedReviewView of(ReviewSchedule r) {
        return new CompletedReviewView(
                r.getId(), r.getStatus(), r.getRating(),
                r.getElapsedDays(), r.getCompletedAt());
    }
}
