package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;

public record CalendarReviewMaterial(
        Long id,
        String title,
        MaterialType type
) {
    public static CalendarReviewMaterial of(StudyMaterial m) {
        return new CalendarReviewMaterial(m.getId(), m.getTitle(), m.getType());
    }
}
