package ds.project.orino.planner.calendar.dto;

import java.time.LocalDate;
import java.util.List;

public record WeeklyScheduleResponse(
        LocalDate startDate,
        LocalDate endDate,
        List<WeeklyDayResponse> days) {
}
