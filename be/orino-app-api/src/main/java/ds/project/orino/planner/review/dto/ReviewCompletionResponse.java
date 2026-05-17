package ds.project.orino.planner.review.dto;

public record ReviewCompletionResponse(
        CompletedReviewView completed,
        ReviewScheduleView nextReview
) {
}
