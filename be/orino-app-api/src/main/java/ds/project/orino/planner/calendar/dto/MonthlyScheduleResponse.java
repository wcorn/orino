package ds.project.orino.planner.calendar.dto;

import java.util.List;

public record MonthlyScheduleResponse(
        int year,
        int month,
        List<MonthlyDayResponse> days) {
}
