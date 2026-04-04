package ds.project.orino.domain.material.repository;

import ds.project.orino.domain.material.entity.StudyMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudyMaterialRepository
        extends JpaRepository<StudyMaterial, Long> {

    List<StudyMaterial> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    Optional<StudyMaterial> findByIdAndMemberId(Long id, Long memberId);
}
