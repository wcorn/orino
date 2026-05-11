package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.planner.unit.entity.StudyUnit;
import ds.project.orino.domain.planner.unit.entity.UnitStatus;

import java.time.LocalDateTime;

public record CompletedUnitResponse(
        Long id,
        String title,
        UnitStatus status,
        LocalDateTime completedAt
) {

    public static CompletedUnitResponse from(StudyUnit unit) {
        return new CompletedUnitResponse(
                unit.getId(),
                unit.getTitle(),
                unit.getStatus(),
                unit.getCompletedAt()
        );
    }
}
