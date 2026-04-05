package ds.project.orino.planner.calendar.dto;

import java.time.LocalDate;
import java.util.List;

public record WeeklyDayResponse(
        LocalDate date,
        int totalBlocks,
        int completedBlocks,
        List<ScheduleBlockResponse> blocks) {
}
