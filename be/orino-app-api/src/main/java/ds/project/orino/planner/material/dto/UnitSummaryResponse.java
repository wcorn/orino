package ds.project.orino.planner.material.dto;

import ds.project.orino.domain.planner.unit.entity.StudyUnit;
import ds.project.orino.domain.planner.unit.entity.UnitStatus;

import java.time.LocalDateTime;

public record UnitSummaryResponse(
        Long id,
        String title,
        Integer sortOrder,
        UnitStatus status,
        LocalDateTime completedAt
) {

    public static UnitSummaryResponse from(StudyUnit unit) {
        return new UnitSummaryResponse(
                unit.getId(),
                unit.getTitle(),
                unit.getSortOrder(),
                unit.getStatus(),
                unit.getCompletedAt()
        );
    }
}
