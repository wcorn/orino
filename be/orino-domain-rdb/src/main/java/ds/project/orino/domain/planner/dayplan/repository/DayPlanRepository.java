package ds.project.orino.domain.planner.dayplan.repository;

import ds.project.orino.domain.planner.dayplan.entity.DayPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DayPlanRepository extends JpaRepository<DayPlan, Long> {

    List<DayPlan> findAllByMemberIdOrderByIdAsc(Long memberId);

    Optional<DayPlan> findByIdAndMemberId(Long id, Long memberId);

    /** 펼침(instances)용 — 활성 플랜만. */
    List<DayPlan> findAllByMemberIdAndEnabledTrue(Long memberId);
}
