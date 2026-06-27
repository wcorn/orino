package ds.project.orino.planner.dayplan.dto;

import java.util.List;

/** 주간 템플릿(요일별 블록 전체). */
public record DayPlanResponse(
        List<DayPlanBlockResponse> blocks
) {
}
