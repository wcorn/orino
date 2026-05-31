package ds.project.orino.planner.review.dto;

import java.time.LocalDate;
import java.util.List;

public record CalendarReviewsResponse(
        LocalDate from,
        LocalDate to,
        List<CalendarReviewItem> reviews
) {
}
