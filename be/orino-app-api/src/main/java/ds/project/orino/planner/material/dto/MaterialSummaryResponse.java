package ds.project.orino.planner.material.dto;

import ds.project.orino.domain.planner.material.entity.MaterialStatus;
import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;

import java.time.LocalDateTime;

public record MaterialSummaryResponse(
        Long id,
        String title,
        MaterialType type,
        MaterialStatus status,
        long totalUnits,
        long completedUnits,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MaterialSummaryResponse of(StudyMaterial material, long totalUnits, long completedUnits) {
        return new MaterialSummaryResponse(
                material.getId(),
                material.getTitle(),
                material.getType(),
                material.getStatus(),
                totalUnits,
                completedUnits,
                material.getCreatedAt(),
                material.getUpdatedAt()
        );
    }
}
