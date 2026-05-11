package ds.project.orino.planner.review.dto;

public record UnitCompletionResponse(
        CompletedUnitResponse unit,
        ReviewResponse firstReview
) {
}
