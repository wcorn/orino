package ds.project.orino.planner.review.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 복습 허브 현황 집계. counts는 목록 길이가 아니라 서버 총계다.
 */
public record ReviewSummaryResponse(
        LocalDate today,
        Counts counts,
        int estimatedMinutes,
        List<Material> materials
) {
    public record Counts(
            long now,
            long overdue,
            long upcoming,
            long doneToday
    ) {
    }

    public record Material(
            Long id,
            String name,
            long due,
            long overdue,
            String nextLabel
    ) {
    }
}
