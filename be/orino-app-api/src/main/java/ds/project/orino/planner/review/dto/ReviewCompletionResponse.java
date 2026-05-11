package ds.project.orino.planner.review.dto;

public record ReviewCompletionResponse(
        ReviewResponse completed,
        ReviewResponse nextReview
) {
}
