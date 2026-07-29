package ds.project.orino.domain.planner.flashcard.repository;

import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.entity.FlashcardType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {

    List<Flashcard> findAllByMaterialIdOrderByCreatedAtAscIdAsc(Long materialId);

    Optional<Flashcard> findByIdAndMemberId(Long id, Long memberId);

    List<Flashcard> findAllByIdIn(Collection<Long> ids);

    /** 같은 양방향 짝 그룹에 속한 카드들. */
    List<Flashcard> findAllBySiblingGroupId(Long siblingGroupId);

    /**
     * 자료의 카드 목록 — {@code (created_at, id)} keyset 페이징 + 검색/종류/복습 상태 필터.
     * <p>
     * 정렬 방향은 {@code Pageable}의 {@code Sort}로 주입하고(createdAt·id 동일 방향),
     * {@code asc}는 커서 비교 부등호를 그 방향에 맞춘다. 둘이 어긋나면 페이지가 건너뛰거나 반복된다.
     * <p>
     * 모든 필터 파라미터는 null이면 무시된다.
     * {@code q}는 호출측에서 소문자 + {@code %} 래핑까지 마친 LIKE 패턴이고,
     * {@code cardType}/{@code siblingRequired}는 종류 필터(pair는 BASIC + siblingGroupId NOT NULL 파생),
     * {@code dueFrom}/{@code dueBefore}는 PENDING 복습 시각 구간(밀림·오늘·예정)이다.
     */
    @Query("""
            SELECT f FROM Flashcard f
            WHERE f.materialId = :materialId
              AND (:q IS NULL
                   OR LOWER(f.front) LIKE :q
                   OR LOWER(f.back) LIKE :q
                   OR LOWER(f.items) LIKE :q)
              AND (:cardType IS NULL OR f.type = :cardType)
              AND (:siblingRequired IS NULL
                   OR (:siblingRequired = TRUE AND f.siblingGroupId IS NOT NULL)
                   OR (:siblingRequired = FALSE AND f.siblingGroupId IS NULL))
              AND ((:dueFrom IS NULL AND :dueBefore IS NULL)
                   OR EXISTS (SELECT 1 FROM ReviewSchedule r
                              WHERE r.flashcardId = f.id
                                AND r.status = ds.project.orino.domain.planner.review.entity.ReviewStatus.PENDING
                                AND (:dueFrom IS NULL OR r.scheduledAt >= :dueFrom)
                                AND (:dueBefore IS NULL OR r.scheduledAt < :dueBefore)))
              AND (:cursorAt IS NULL
                   OR (:asc = TRUE
                       AND (f.createdAt > :cursorAt
                            OR (f.createdAt = :cursorAt AND f.id > :cursorId)))
                   OR (:asc = FALSE
                       AND (f.createdAt < :cursorAt
                            OR (f.createdAt = :cursorAt AND f.id < :cursorId))))
            """)
    List<Flashcard> findPage(@Param("materialId") Long materialId,
                             @Param("q") String q,
                             @Param("cardType") FlashcardType cardType,
                             @Param("siblingRequired") Boolean siblingRequired,
                             @Param("dueFrom") Instant dueFrom,
                             @Param("dueBefore") Instant dueBefore,
                             @Param("asc") boolean asc,
                             @Param("cursorAt") Instant cursorAt,
                             @Param("cursorId") Long cursorId,
                             Pageable pageable);

    /** {@link #findPage}와 같은 필터의 총 개수(커서 무관). 목록 상단 "N장" 표시용. */
    @Query("""
            SELECT COUNT(f) FROM Flashcard f
            WHERE f.materialId = :materialId
              AND (:q IS NULL
                   OR LOWER(f.front) LIKE :q
                   OR LOWER(f.back) LIKE :q
                   OR LOWER(f.items) LIKE :q)
              AND (:cardType IS NULL OR f.type = :cardType)
              AND (:siblingRequired IS NULL
                   OR (:siblingRequired = TRUE AND f.siblingGroupId IS NOT NULL)
                   OR (:siblingRequired = FALSE AND f.siblingGroupId IS NULL))
              AND ((:dueFrom IS NULL AND :dueBefore IS NULL)
                   OR EXISTS (SELECT 1 FROM ReviewSchedule r
                              WHERE r.flashcardId = f.id
                                AND r.status = ds.project.orino.domain.planner.review.entity.ReviewStatus.PENDING
                                AND (:dueFrom IS NULL OR r.scheduledAt >= :dueFrom)
                                AND (:dueBefore IS NULL OR r.scheduledAt < :dueBefore)))
            """)
    long countPage(@Param("materialId") Long materialId,
                   @Param("q") String q,
                   @Param("cardType") FlashcardType cardType,
                   @Param("siblingRequired") Boolean siblingRequired,
                   @Param("dueFrom") Instant dueFrom,
                   @Param("dueBefore") Instant dueBefore);
}
