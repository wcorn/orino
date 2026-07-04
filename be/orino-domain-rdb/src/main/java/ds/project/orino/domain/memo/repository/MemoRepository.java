package ds.project.orino.domain.memo.repository;

import ds.project.orino.domain.memo.entity.Memo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemoRepository extends JpaRepository<Memo, Long> {

    List<Memo> findAllByMemberIdOrderBySortOrderAscIdAsc(Long memberId);

    Optional<Memo> findByIdAndMemberId(Long id, Long memberId);

    @Query("""
            SELECT COALESCE(MAX(m.sortOrder), -1)
            FROM Memo m
            WHERE m.memberId = :memberId
              AND (:parentId IS NULL AND m.parentId IS NULL
                   OR m.parentId = :parentId)
            """)
    int findMaxSortOrder(@Param("memberId") Long memberId, @Param("parentId") Long parentId);
}
