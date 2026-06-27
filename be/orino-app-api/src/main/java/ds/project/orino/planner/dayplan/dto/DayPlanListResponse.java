package ds.project.orino.planner.dayplan.dto;

import java.util.List;

public record DayPlanListResponse(
        List<DayPlanResponse> plans
) {
}
