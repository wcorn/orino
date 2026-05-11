package ds.project.orino.support;

import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.unit.entity.StudyUnit;

public final class StudyMaterialFixture {

    private StudyMaterialFixture() {
    }

    public static StudyMaterial create(Long memberId) {
        return new StudyMaterial(memberId, "이펙티브 자바", MaterialType.BOOK);
    }

    public static StudyMaterial create(Long memberId, String title, MaterialType type) {
        return new StudyMaterial(memberId, title, type);
    }

    public static StudyUnit createUnit(Long memberId, Long materialId, int sortOrder) {
        return new StudyUnit(memberId, materialId, "아이템 " + sortOrder, sortOrder);
    }
}
