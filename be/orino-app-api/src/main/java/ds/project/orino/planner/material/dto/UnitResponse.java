package ds.project.orino.planner.material.dto;

import ds.project.orino.domain.planner.unit.entity.StudyUnit;
import ds.project.orino.domain.planner.unit.entity.UnitStatus;

import java.time.LocalDateTime;

public record UnitResponse(
        Long id,
        Long materialId,
        String title,
        Integer sortOrder,
        UnitStatus status,
        LocalDateTime completedAt
) {

    public static UnitResponse from(StudyUnit unit) {
        return new UnitResponse(
                unit.getId(),
                unit.getMaterialId(),
                unit.getTitle(),
                unit.getSortOrder(),
                unit.getStatus(),
                unit.getCompletedAt()
        );
    }
}
