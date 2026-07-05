package ds.project.orino.domain.planner.goal.repository;

import ds.project.orino.domain.planner.goal.entity.MonthlyGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MonthlyGoalRepository extends JpaRepository<MonthlyGoal, Long> {

    Optional<MonthlyGoal> findByMemberIdAndYearAndMonth(Long memberId, int year, int month);
}
