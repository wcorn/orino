package ds.project.orino.planner.dayplan.dto;

import ds.project.orino.domain.planner.dayplan.entity.DayPlanBlock;
import ds.project.orino.planner.dayplan.WeeklyPlanTime;

/** 주간 블록 응답. 시간은 "HH:mm"(종료 "24:00"=자정). */
public record DayPlanBlockResponse(
        Long id,
        int dayOfWeek,
        String startTime,
        String endTime,
        String label,
        String color
) {

    public static DayPlanBlockResponse of(DayPlanBlock block) {
        return new DayPlanBlockResponse(
                block.getId(),
                block.getDayOfWeek(),
                WeeklyPlanTime.toHhmm(block.getStartMinute()),
                WeeklyPlanTime.toHhmm(block.getEndMinute()),
                block.getLabel(),
                block.getColor());
    }
}
