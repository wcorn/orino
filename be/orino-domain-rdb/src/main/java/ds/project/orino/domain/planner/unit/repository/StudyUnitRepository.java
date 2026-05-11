package ds.project.orino.domain.planner.unit.repository;

import ds.project.orino.domain.planner.unit.entity.StudyUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudyUnitRepository extends JpaRepository<StudyUnit, Long> {

    List<StudyUnit> findAllByMaterialIdOrderBySortOrderAsc(Long materialId);

    Optional<StudyUnit> findByIdAndMemberId(Long id, Long memberId);

    @Query("SELECT COALESCE(MAX(u.sortOrder), 0) FROM StudyUnit u WHERE u.materialId = :materialId")
    int findMaxSortOrderByMaterialId(@Param("materialId") Long materialId);

    void deleteAllByMaterialId(Long materialId);

    @Query("SELECT u.id FROM StudyUnit u WHERE u.materialId = :materialId")
    List<Long> findIdsByMaterialId(@Param("materialId") Long materialId);

    List<StudyUnit> findAllByIdIn(Collection<Long> ids);

    @Query("""
            SELECT u.materialId AS materialId,
                   COUNT(u) AS totalUnits,
                   SUM(CASE WHEN u.status = ds.project.orino.domain.planner.unit.entity.UnitStatus.COMPLETED
                            THEN 1 ELSE 0 END) AS completedUnits
            FROM StudyUnit u
            WHERE u.materialId IN :materialIds
            GROUP BY u.materialId
            """)
    List<UnitCountProjection> countByMaterialIds(@Param("materialIds") Collection<Long> materialIds);

    interface UnitCountProjection {
        Long getMaterialId();
        Long getTotalUnits();
        Long getCompletedUnits();
    }
}
