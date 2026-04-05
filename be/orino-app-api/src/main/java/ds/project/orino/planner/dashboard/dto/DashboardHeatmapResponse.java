package ds.project.orino.planner.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardHeatmapResponse(
        int year,
        List<DayAchievement> days) {

    public record DayAchievement(
            LocalDate date,
            int achievementRate) {
    }
}
