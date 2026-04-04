package ds.project.orino.domain.fixedschedule.repository;

import ds.project.orino.domain.fixedschedule.entity.FixedSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FixedScheduleRepository extends JpaRepository<FixedSchedule, Long> {

    List<FixedSchedule> findByMemberIdOrderByStartTime(Long memberId);

    Optional<FixedSchedule> findByIdAndMemberId(Long id, Long memberId);
}
