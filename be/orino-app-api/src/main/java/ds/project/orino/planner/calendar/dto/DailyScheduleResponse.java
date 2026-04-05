package ds.project.orino.planner.calendar.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyScheduleResponse(
        LocalDate date,
        int totalBlocks,
        int completedBlocks,
        List<ScheduleBlockResponse> blocks,
        List<WarningResponse> warnings) {
}
