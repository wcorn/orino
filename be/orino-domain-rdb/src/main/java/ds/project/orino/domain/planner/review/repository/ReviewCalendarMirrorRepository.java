package ds.project.orino.domain.planner.review.repository;

import ds.project.orino.domain.planner.review.entity.ReviewCalendarMirror;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReviewCalendarMirrorRepository extends JpaRepository<ReviewCalendarMirror, Long> {

    Optional<ReviewCalendarMirror> findByMemberIdAndDueDate(Long memberId, LocalDate dueDate);

    List<ReviewCalendarMirror> findAllByMemberId(Long memberId);

    void deleteByMemberIdAndDueDate(Long memberId, LocalDate dueDate);

    void deleteByMemberId(Long memberId);
}
