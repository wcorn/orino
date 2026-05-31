package ds.project.orino.domain.planner.review.repository;

import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewScheduleRepository extends JpaRepository<ReviewSchedule, Long> {

    List<ReviewSchedule> findAllByFlashcardIdInAndStatusOrderByScheduledAtAscIdAsc(
            Collection<Long> flashcardIds, ReviewStatus status);

    Optional<ReviewSchedule> findByIdAndMemberId(Long id, Long memberId);

    List<ReviewSchedule> findAllByMemberIdAndStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
            Long memberId, ReviewStatus status, LocalDateTime scheduledAt);

    List<ReviewSchedule> findAllByMemberIdAndScheduledAtBetweenOrderByScheduledAtAscIdAsc(
            Long memberId, LocalDateTime from, LocalDateTime to);
}
