package ds.project.orino.planner.material.dto;

import ds.project.orino.domain.planner.material.entity.MaterialStatus;
import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;

import java.time.LocalDateTime;

public record MaterialResponse(
        Long id,
        String title,
        MaterialType type,
        MaterialStatus status,
        long flashcardCount,
        long dueReviewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MaterialResponse of(StudyMaterial m, long flashcardCount, long dueReviewCount) {
        return new MaterialResponse(
                m.getId(),
                m.getTitle(),
                m.getType(),
                m.getStatus(),
                flashcardCount,
                dueReviewCount,
                m.getCreatedAt(),
                m.getUpdatedAt());
    }
}
