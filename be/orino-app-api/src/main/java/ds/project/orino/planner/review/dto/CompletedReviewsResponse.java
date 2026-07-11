package ds.project.orino.planner.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompletedReviewsResponse(
        List<CompletedReviewItem> items,
        String nextCursor,
        boolean hasNext
) {
}
