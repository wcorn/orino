package ds.project.orino.planner.dayplan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 플랜 생성/수정 요청. {@code blocks}는 최종 상태(declarative) — id 있으면 수정, 없으면 신규, 누락은 삭제.
 */
public record DayPlanRequest(
        @NotBlank String name,
        String color,
        @NotNull @Valid DayPlanRecurrence recurrence,
        @NotNull @Valid List<DayPlanBlockRequest> blocks
) {
}
