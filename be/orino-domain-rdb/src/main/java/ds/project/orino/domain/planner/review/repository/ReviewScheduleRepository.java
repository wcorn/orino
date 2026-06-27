package ds.project.orino.domain.planner.review.repository;

import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewScheduleRepository extends JpaRepository<ReviewSchedule, Long> {

    List<ReviewSchedule> findAllByFlashcardIdInAndStatusOrderByScheduledAtAscIdAsc(
            Collection<Long> flashcardIds, ReviewStatus status);

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
}
