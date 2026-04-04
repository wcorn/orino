package ds.project.orino.planner.material.dto;

import ds.project.orino.domain.material.entity.DeadlineMode;
import ds.project.orino.domain.material.entity.MaterialStatus;
import ds.project.orino.domain.material.entity.MaterialType;
import ds.project.orino.domain.material.entity.StudyMaterial;

import java.time.LocalDate;
import java.util.List;

public record MaterialDetailResponse(
        Long id,
        String title,
        MaterialType type,
        Long categoryId,
        Long goalId,
        LocalDate deadline,
        DeadlineMode deadlineMode,
        MaterialStatus status,
        List<UnitResponse> units,
        AllocationResponse allocation,
        ReviewConfigResponse reviewConfig
) {

    public static MaterialDetailResponse from(StudyMaterial m) {
        return new MaterialDetailResponse(
                m.getId(),
                m.getTitle(),
                m.getType(),
                m.getCategory() != null
                        ? m.getCategory().getId() : null,
                m.getGoal() != null
                        ? m.getGoal().getId() : null,
                m.getDeadline(),
                m.getDeadlineMode(),
                m.getStatus(),
                m.getUnits().stream()
                        .map(UnitResponse::from)
                        .toList(),
                m.getAllocation() != null
                        ? AllocationResponse.from(m.getAllocation())
                        : null,
                m.getReviewConfig() != null
                        ? ReviewConfigResponse.from(m.getReviewConfig())
                        : null
        );
    }
}
