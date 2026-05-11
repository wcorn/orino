package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.planner.review.entity.Rating;
import jakarta.validation.constraints.NotNull;

public record ReviewCompletionRequest(
        @NotNull(message = "평가를 입력해주세요.")
        Rating rating
) {
}
