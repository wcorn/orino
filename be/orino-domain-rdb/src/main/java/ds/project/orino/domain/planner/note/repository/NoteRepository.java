package ds.project.orino.domain.planner.note.repository;

import ds.project.orino.domain.planner.note.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findAllByMaterialIdOrderBySortOrderAscIdAsc(Long materialId);

    Optional<Note> findByIdAndMemberId(Long id, Long memberId);

    @Query("""
            SELECT COALESCE(MAX(n.sortOrder), -1)
            FROM Note n
            WHERE n.materialId = :materialId
              AND (:parentId IS NULL AND n.parentId IS NULL
                   OR n.parentId = :parentId)
            """)
    int findMaxSortOrder(@Param("materialId") Long materialId, @Param("parentId") Long parentId);
}
