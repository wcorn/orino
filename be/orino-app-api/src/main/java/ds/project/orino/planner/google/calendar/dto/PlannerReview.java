package ds.project.orino.planner.google.calendar.dto;

import java.time.Instant;

/**
 * 통합 피드의 복습 항목(읽기 전용 오버레이). orino가 복습의 source of truth.
 * 평가는 기존 오늘 복습 화면에서만 하므로 {@code readOnly=true}.
 */
public record PlannerReview(
        Long id,
        Instant scheduledAt,
        String status,
        String materialTitle,
        String front,
        boolean readOnly,
        String source
) {
}
