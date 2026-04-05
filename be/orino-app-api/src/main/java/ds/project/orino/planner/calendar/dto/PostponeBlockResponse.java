package ds.project.orino.planner.calendar.dto;

import java.time.LocalDate;

public record PostponeBlockResponse(
        Long blockId,
        LocalDate postponedTo,
        DailyProgress dailyProgress) {
}
