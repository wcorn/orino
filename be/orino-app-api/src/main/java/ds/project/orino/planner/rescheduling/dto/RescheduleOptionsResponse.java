package ds.project.orino.planner.rescheduling.dto;

import java.util.List;

public record RescheduleOptionsResponse(
        int missedDays,
        int missedItems,
        List<RescheduleOption> options) {
}
