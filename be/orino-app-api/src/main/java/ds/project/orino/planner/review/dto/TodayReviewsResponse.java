package ds.project.orino.planner.review.dto;

import java.time.LocalDate;
import java.util.List;

public record TodayReviewsResponse(
        LocalDate today,
        List<TodayReviewItem> reviews
) {
}
