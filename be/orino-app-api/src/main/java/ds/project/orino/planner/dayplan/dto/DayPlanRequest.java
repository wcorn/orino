package ds.project.orino.planner.dayplan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 주간 템플릿 전량 교체 요청. {@code blocks} = 멤버의 최종 주간 상태. */
public record DayPlanRequest(
        @NotNull @Valid List<DayPlanBlockRequest> blocks
) {
}
