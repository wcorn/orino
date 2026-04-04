package ds.project.orino.domain.material.repository;

import ds.project.orino.domain.material.entity.StudyUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudyUnitRepository
        extends JpaRepository<StudyUnit, Long> {

    Optional<StudyUnit> findByIdAndMaterialId(Long id, Long materialId);
}
