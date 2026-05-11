package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;

public record ReviewMaterialResponse(
        Long id,
        String title,
        MaterialType type
) {

    public static ReviewMaterialResponse from(StudyMaterial material) {
        return new ReviewMaterialResponse(material.getId(), material.getTitle(), material.getType());
    }
}
