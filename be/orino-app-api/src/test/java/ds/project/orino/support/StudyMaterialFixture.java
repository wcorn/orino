package ds.project.orino.support;

import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;

public final class StudyMaterialFixture {

    private StudyMaterialFixture() {
    }

    public static StudyMaterial create(Long memberId) {
        return new StudyMaterial(memberId, "이펙티브 자바", MaterialType.BOOK);
    }

    public static StudyMaterial create(Long memberId, String title, MaterialType type) {
        return new StudyMaterial(memberId, title, type);
    }
}
