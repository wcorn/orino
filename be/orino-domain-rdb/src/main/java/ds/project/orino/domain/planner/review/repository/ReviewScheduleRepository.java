package ds.project.orino.domain.planner.review.repository;

import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface ReviewScheduleRepository extends JpaRepository<ReviewSchedule, Long> {

    Optional<ReviewSchedule> findByIdAndMemberId(Long id, Long memberId);

    void deleteAllByStudyUnitId(Long studyUnitId);

    void deleteAllByStudyUnitIdIn(Collection<Long> studyUnitIds);
}
