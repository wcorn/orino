package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.planner.unit.entity.StudyUnit;

public record ReviewUnitResponse(
        Long id,
        String title,
        ReviewMaterialResponse material
) {

    public static ReviewUnitResponse of(StudyUnit unit, ReviewMaterialResponse material) {
        return new ReviewUnitResponse(unit.getId(), unit.getTitle(), material);
    }
}
