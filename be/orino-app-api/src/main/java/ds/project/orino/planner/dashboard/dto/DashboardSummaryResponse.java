package ds.project.orino.planner.dashboard.dto;

import java.util.List;

public record DashboardSummaryResponse(
        List<GoalSummary> goals,
        ThisWeek thisWeek,
        Streaks streaks,
        TodayProgress todayProgress) {

    public record GoalSummary(
            Long id,
            String title,
            int progressPercent,
            String paceStatus) {
    }

    public record ThisWeek(
            int studyMinutes,
            int reviewCompletionRate) {
    }

    public record Streaks(
            OverallStreak overall,
            List<RoutineStreak> routines) {
    }

    public record OverallStreak(
            int currentCount,
            int longestCount) {
    }

    public record RoutineStreak(
            Long routineId,
            String title,
            int currentCount) {
    }

    public record TodayProgress(
            int totalBlocks,
            int completedBlocks) {
    }
}
