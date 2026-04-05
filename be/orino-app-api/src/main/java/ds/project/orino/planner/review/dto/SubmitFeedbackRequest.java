package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.review.entity.DifficultyFeedback;
import jakarta.validation.constraints.NotNull;

public record SubmitFeedbackRequest(
        @NotNull DifficultyFeedback feedback
) {
}
