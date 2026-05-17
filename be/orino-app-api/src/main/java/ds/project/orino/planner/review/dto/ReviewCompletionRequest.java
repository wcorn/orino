package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.planner.review.entity.Rating;
import jakarta.validation.constraints.NotNull;

public record ReviewCompletionRequest(
        @NotNull(message = "rating은 필수입니다.")
        Rating rating
) {
}
