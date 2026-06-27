package ds.project.orino.planner.dayplan.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import ds.project.orino.domain.planner.dayplan.entity.DayPlanBlock;

import java.time.LocalTime;

public record DayPlanBlockResponse(
        Long id,
        int dayOfWeek,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime endTime,
        String label,
        String color
) {

    public static DayPlanBlockResponse of(DayPlanBlock block) {
        return new DayPlanBlockResponse(
                block.getId(), block.getDayOfWeek(), block.getStartTime(), block.getEndTime(),
                block.getLabel(), block.getColor());
    }
}
