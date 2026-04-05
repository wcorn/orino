package ds.project.orino.planner.dashboard.dto;

import java.util.List;

public record DashboardStatisticsResponse(
        StatisticsPeriod period,
        int totalStudyMinutes,
        int totalReviewMinutes,
        int reviewCompletionRate,
        List<CategoryBreakdown> categoryBreakdown) {

    public record CategoryBreakdown(
            Long categoryId,
            String name,
            String color,
            int minutes,
            int percent) {
    }
}
