package ds.project.orino.planner.calendar.dto;

import ds.project.orino.domain.calendar.entity.BlockStatus;

public record CompleteBlockResponse(
        Long blockId,
        BlockStatus status,
        BlockEffect effect,
        DailyProgress dailyProgress) {
}
