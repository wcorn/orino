package ds.project.orino.domain.planner.review.repository;

import ds.project.orino.domain.planner.flashcard.entity.FlashcardType;
import ds.project.orino.domain.planner.review.entity.Rating;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewScheduleRepository extends JpaRepository<ReviewSchedule, Long> {

    List<ReviewSchedule> findAllByFlashcardIdInAndStatusOrderByScheduledAtAscIdAsc(
            Collection<Long> flashcardIds, ReviewStatus status);

    /** Sibling burying용 — 짝 카드들의 현재 due(scheduled_at ≤ now) PENDING 복습. */
    List<ReviewSchedule> findAllByFlashcardIdInAndStatusAndScheduledAtLessThanEqual(
            Collection<Long> flashcardIds, ReviewStatus status, Instant scheduledAt);

    Optional<ReviewSchedule> findByIdAndMemberId(Long id, Long memberId);

    List<ReviewSchedule> findAllByMemberIdAndStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
            Long memberId, ReviewStatus status, Instant scheduledAt);

    List<ReviewSchedule> findAllByMemberIdAndScheduledAtBetweenOrderByScheduledAtAscIdAsc(
            Long memberId, Instant from, Instant to);

    /**
     * dueDate 묶음 미러용 — 정확히 해당 04:00 롤오버 시각에 예약된 복습만 조회한다.
     * AGAIN(now+10분, 분 단위)은 이 시각과 일치하지 않아 자연히 제외된다.
     */
    List<ReviewSchedule> findAllByMemberIdAndStatusAndScheduledAt(
            Long memberId, ReviewStatus status, Instant scheduledAt);

    /** 미러 enable 백필용 — 멤버의 모든 PENDING 복습(과거·미래 포함). */
    List<ReviewSchedule> findAllByMemberIdAndStatus(Long memberId, ReviewStatus status);

    /** 오늘 완료 수 — completed_at 이 [todayStart, tomorrowStart) 인 COMPLETED. */
    long countByMemberIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            Long memberId, ReviewStatus status, Instant todayStart, Instant tomorrowStart);

    /**
     * 앞으로의 복습 — 전 PENDING을 {@code scheduled_at ASC, id ASC}로 keyset 페이징한다.
     * {@code upperBound}는 scope/when 필터를 하나로 합친 상한(scheduled_at &lt; upperBound),
     * {@code cardType}/{@code siblingRequired}는 종류 필터(pair는 BASIC + siblingGroupId NOT NULL 파생),
     * {@code cursorAt}/{@code cursorId}는 이전 페이지 마지막 keyset이다. 모든 필터 파라미터는 null이면 무시된다.
     */
    @Query("""
            SELECT r FROM ReviewSchedule r, Flashcard f
            WHERE r.flashcardId = f.id
              AND r.memberId = :memberId
              AND r.status = ds.project.orino.domain.planner.review.entity.ReviewStatus.PENDING
              AND (:materialId IS NULL OR f.materialId = :materialId)
              AND (:upperBound IS NULL OR r.scheduledAt < :upperBound)
              AND (:cardType IS NULL OR f.type = :cardType)
              AND (:siblingRequired IS NULL
                   OR (:siblingRequired = TRUE AND f.siblingGroupId IS NOT NULL)
                   OR (:siblingRequired = FALSE AND f.siblingGroupId IS NULL))
              AND (:cursorAt IS NULL
                   OR r.scheduledAt > :cursorAt
                   OR (r.scheduledAt = :cursorAt AND r.id > :cursorId))
            ORDER BY r.scheduledAt ASC, r.id ASC
            """)
    List<ReviewSchedule> findUpcoming(@Param("memberId") Long memberId,
                                      @Param("materialId") Long materialId,
                                      @Param("upperBound") Instant upperBound,
                                      @Param("cardType") FlashcardType cardType,
                                      @Param("siblingRequired") Boolean siblingRequired,
                                      @Param("cursorAt") Instant cursorAt,
                                      @Param("cursorId") Long cursorId,
                                      Pageable pageable);

    /**
     * 완료된 복습 — COMPLETED를 {@code completed_at DESC, id DESC}로 keyset 페이징한다.
     * {@code materialId}/{@code grade}는 필터(null이면 무시), {@code cursorAt}/{@code cursorId}는
     * 이전 페이지 마지막 keyset이다.
     */
    @Query("""
            SELECT r FROM ReviewSchedule r, Flashcard f
            WHERE r.flashcardId = f.id
              AND r.memberId = :memberId
              AND r.status = ds.project.orino.domain.planner.review.entity.ReviewStatus.COMPLETED
              AND (:materialId IS NULL OR f.materialId = :materialId)
              AND (:grade IS NULL OR r.rating = :grade)
              AND (:cursorAt IS NULL
                   OR r.completedAt < :cursorAt
                   OR (r.completedAt = :cursorAt AND r.id < :cursorId))
            ORDER BY r.completedAt DESC, r.id DESC
            """)
    List<ReviewSchedule> findCompleted(@Param("memberId") Long memberId,
                                       @Param("materialId") Long materialId,
                                       @Param("grade") Rating grade,
                                       @Param("cursorAt") Instant cursorAt,
                                       @Param("cursorId") Long cursorId,
                                       Pageable pageable);
}
