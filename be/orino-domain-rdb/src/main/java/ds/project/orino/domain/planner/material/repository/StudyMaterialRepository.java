package ds.project.orino.domain.planner.material.repository;

import ds.project.orino.domain.planner.material.entity.MaterialStatus;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudyMaterialRepository extends JpaRepository<StudyMaterial, Long> {

    List<StudyMaterial> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<StudyMaterial> findAllByMemberIdAndStatusOrderByCreatedAtDesc(Long memberId, MaterialStatus status);

    Optional<StudyMaterial> findByIdAndMemberId(Long id, Long memberId);
}
