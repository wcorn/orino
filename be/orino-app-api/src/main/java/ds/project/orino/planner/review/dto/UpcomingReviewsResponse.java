package ds.project.orino.planner.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpcomingReviewsResponse(
        LocalDate today,
        List<UpcomingReviewItem> items,
        String nextCursor,
        boolean hasNext
) {
}
