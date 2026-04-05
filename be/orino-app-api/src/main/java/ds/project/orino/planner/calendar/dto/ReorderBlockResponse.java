package ds.project.orino.planner.calendar.dto;

import java.time.LocalTime;

public record ReorderBlockResponse(
        Long blockId,
        LocalTime startTime,
        LocalTime endTime,
        boolean isPinned) {
}
