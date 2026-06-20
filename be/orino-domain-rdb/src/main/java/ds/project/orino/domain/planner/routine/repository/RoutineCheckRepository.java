package ds.project.orino.domain.planner.routine.repository;

import ds.project.orino.domain.planner.routine.entity.RoutineCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoutineCheckRepository extends JpaRepository<RoutineCheck, Long> {

    Optional<RoutineCheck> findByMemberIdAndRecurringEventIdAndInstanceDate(
            Long memberId, String recurringEventId, LocalDate instanceDate);

    boolean existsByMemberIdAndRecurringEventIdAndInstanceDate(
            Long memberId, String recurringEventId, LocalDate instanceDate);

    void deleteByMemberIdAndRecurringEventIdAndInstanceDate(
            Long memberId, String recurringEventId, LocalDate instanceDate);

    /** 통합 피드 done 조인용 batch 로드: [from, to] 구간의 완료 행을 한 번에 가져온다(N+1 회피). */
    List<RoutineCheck> findByMemberIdAndInstanceDateBetween(
            Long memberId, LocalDate from, LocalDate to);
}
