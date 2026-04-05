package ds.project.orino.planner.dashboard.dto;

import java.util.List;

public record DashboardStreaksResponse(
        Overall overall,
        List<RoutineStreak> routines,
        int freezeRemaining) {

    public record Overall(
            int currentCount,
            int longestCount) {
    }

    public record RoutineStreak(
            Long routineId,
            String title,
            int currentCount,
            int longestCount) {
    }
}
