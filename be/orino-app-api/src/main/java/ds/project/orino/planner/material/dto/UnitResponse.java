package ds.project.orino.planner.material.dto;

import ds.project.orino.domain.material.entity.StudyUnit;
import ds.project.orino.domain.material.entity.UnitDifficulty;
import ds.project.orino.domain.material.entity.UnitStatus;

import java.time.LocalDateTime;

public record UnitResponse(
        Long id,
        String title,
        int sortOrder,
        int estimatedMinutes,
        UnitDifficulty difficulty,
        UnitStatus status,
        LocalDateTime completedAt
) {

    public static UnitResponse from(StudyUnit u) {
        return new UnitResponse(
                u.getId(),
                u.getTitle(),
                u.getSortOrder(),
                u.getEstimatedMinutes(),
                u.getDifficulty(),
                u.getStatus(),
                u.getCompletedAt()
        );
    }
}
