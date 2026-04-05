package ds.project.orino.planner.calendar.dto;

import ds.project.orino.domain.calendar.entity.BlockStatus;
import ds.project.orino.domain.calendar.entity.BlockType;

import java.time.LocalTime;

public record ScheduleBlockResponse(
        Long id,
        BlockType blockType,
        Long referenceId,
        String title,
        String categoryName,
        String categoryColor,
        LocalTime startTime,
        LocalTime endTime,
        BlockStatus status,
        boolean isPinned) {
}
