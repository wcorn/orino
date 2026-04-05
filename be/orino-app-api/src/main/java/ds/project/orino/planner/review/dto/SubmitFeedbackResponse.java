package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.review.entity.DifficultyFeedback;
import ds.project.orino.domain.review.entity.ReviewStatus;

import java.time.LocalDate;

public record SubmitFeedbackResponse(
        Long reviewId,
        ReviewStatus status,
        DifficultyFeedback feedback,
        LocalDate nextReviewDate,
        Integer nextSequence
) {
}
