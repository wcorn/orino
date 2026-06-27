package ds.project.orino.planner.dayplan.dto;

import java.util.List;

/** 플랜 상세(생성/수정/조회 공용). */
public record DayPlanResponse(
        Long id,
        String name,
        String color,
        boolean enabled,
        DayPlanRecurrence recurrence,
        String recurrenceText,
        List<DayPlanBlockResponse> blocks
) {
}
