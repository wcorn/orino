package ds.project.orino.domain.planner.lifelog.repository;

import ds.project.orino.domain.planner.lifelog.entity.Moment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MomentRepository extends JpaRepository<Moment, Long> {

    Optional<Moment> findByIdAndMemberId(Long id, Long memberId);

    /** 피드 기본 정렬(역시간순). 커서 페이지네이션은 {@link #findFeed}를 쓴다. */
    List<Moment> findAllByMemberIdOrderByOccurredAtDescIdDesc(Long memberId);

    /**
     * 피드 커서 페이지네이션. 역시간순(occurred_at DESC, id DESC), 선택 필터(tag·기간).
     * 커서는 {@code (cursorAt, cursorId)} — null이면 첫 페이지. limit은 {@code pageable}로 준다.
     */
    @Query("""
            SELECT m FROM Moment m
            WHERE m.memberId = :memberId
              AND (:from IS NULL OR m.occurredAt >= :from)
              AND (:to IS NULL OR m.occurredAt <= :to)
              AND (:tag IS NULL OR EXISTS (
                    SELECT 1 FROM MomentTag t WHERE t.momentId = m.id AND t.name = :tag))
              AND (:cursorAt IS NULL
                    OR m.occurredAt < :cursorAt
                    OR (m.occurredAt = :cursorAt AND m.id < :cursorId))
            ORDER BY m.occurredAt DESC, m.id DESC
            """)
    List<Moment> findFeed(@Param("memberId") Long memberId,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          @Param("tag") String tag,
                          @Param("cursorAt") Instant cursorAt,
                          @Param("cursorId") Long cursorId,
                          Pageable pageable);
}
