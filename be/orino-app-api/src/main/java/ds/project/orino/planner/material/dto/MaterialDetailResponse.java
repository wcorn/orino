package ds.project.orino.planner.material.dto;

import ds.project.orino.domain.planner.material.entity.MaterialStatus;
import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.unit.entity.StudyUnit;
import ds.project.orino.domain.planner.unit.entity.UnitStatus;

import java.time.LocalDateTime;
import java.util.List;

public record MaterialDetailResponse(
        Long id,
        String title,
        MaterialType type,
        MaterialStatus status,
        long totalUnits,
        long completedUnits,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<UnitSummaryResponse> units
) {

    public static MaterialDetailResponse of(StudyMaterial material, List<StudyUnit> units) {
        long completed = units.stream()
                .filter(u -> u.getStatus() == UnitStatus.COMPLETED)
                .count();
        return new MaterialDetailResponse(
                material.getId(),
                material.getTitle(),
                material.getType(),
                material.getStatus(),
                units.size(),
                completed,
                material.getCreatedAt(),
                material.getUpdatedAt(),
                units.stream().map(UnitSummaryResponse::from).toList()
        );
    }
}
