package ds.project.orino.planner.material.dto;

import ds.project.orino.domain.material.entity.DeadlineMode;
import ds.project.orino.domain.material.entity.MaterialStatus;
import ds.project.orino.domain.material.entity.MaterialType;
import ds.project.orino.domain.material.entity.StudyMaterial;

import java.time.LocalDate;

public record MaterialResponse(
        Long id,
        String title,
        MaterialType type,
        Long categoryId,
        Long goalId,
        LocalDate deadline,
        DeadlineMode deadlineMode,
        MaterialStatus status,
        int totalUnits,
        long completedUnits
) {

    public static MaterialResponse from(StudyMaterial m) {
        return new MaterialResponse(
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
                m.getUnits().size(),
                m.getCompletedUnits()
        );
    }
}
