package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;

public record ReviewCardMaterial(
        Long id,
        String title,
        MaterialType type
) {
    public static ReviewCardMaterial of(StudyMaterial m) {
        return new ReviewCardMaterial(m.getId(), m.getTitle(), m.getType());
    }
}
