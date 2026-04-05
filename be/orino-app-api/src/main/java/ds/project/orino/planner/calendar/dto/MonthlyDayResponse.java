package ds.project.orino.planner.calendar.dto;

import ds.project.orino.domain.calendar.entity.BlockType;

import java.time.LocalDate;
import java.util.List;

public record MonthlyDayResponse(
        LocalDate date,
        int totalBlocks,
        int completedBlocks,
        List<BlockType> blockTypes) {
}
