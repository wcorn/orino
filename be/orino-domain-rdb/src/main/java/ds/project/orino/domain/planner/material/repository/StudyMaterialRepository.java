package ds.project.orino.domain.planner.material.repository;

import ds.project.orino.domain.planner.material.entity.MaterialStatus;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudyMaterialRepository extends JpaRepository<StudyMaterial, Long> {

    List<StudyMaterial> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<StudyMaterial> findAllByMemberIdAndStatusOrderByCreatedAtDesc(Long memberId, MaterialStatus status);

    Optional<StudyMaterial> findByIdAndMemberId(Long id, Long memberId);

    List<StudyMaterial> findAllByIdIn(Collection<Long> ids);

    interface MaterialCountRow {
        Long getMaterialId();
        Long getCount();
    }

    @Query(value = """
            SELECT material_id AS materialId, COUNT(*) AS count
            FROM flashcard
            WHERE material_id IN (:materialIds)
            GROUP BY material_id
            """, nativeQuery = true)
    List<MaterialCountRow> countFlashcardsByMaterialIds(@Param("materialIds") Collection<Long> materialIds);

    // member_id 조건을 명시해 review_schedule의 (member_id, scheduled_at, status)
    // 인덱스를 활용한다. (member_id 없이 flashcard JOIN만으로는 인덱스 효율이 낮음)
    @Query(value = """
            SELECT f.material_id AS materialId, COUNT(r.id) AS count
            FROM review_schedule r
            JOIN flashcard f ON f.id = r.flashcard_id
            WHERE r.member_id = :memberId
              AND f.material_id IN (:materialIds)
              AND r.status = 'PENDING'
              AND r.scheduled_at <= :now
            GROUP BY f.material_id
            """, nativeQuery = true)
    List<MaterialCountRow> countDueReviewsByMaterialIds(
            @Param("memberId") Long memberId,
            @Param("materialIds") Collection<Long> materialIds,
            @Param("now") Instant now);
}
