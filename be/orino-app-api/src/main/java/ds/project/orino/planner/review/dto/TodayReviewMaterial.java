package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;

public record TodayReviewMaterial(
        Long id,
        String title,
        MaterialType type
) {
    public static TodayReviewMaterial of(StudyMaterial m) {
        return new TodayReviewMaterial(m.getId(), m.getTitle(), m.getType());
    }
}
