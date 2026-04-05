package ds.project.orino.domain.streak.repository;

import ds.project.orino.domain.streak.entity.Streak;
import ds.project.orino.domain.streak.entity.StreakType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StreakRepository extends JpaRepository<Streak, Long> {

    Optional<Streak> findByMemberIdAndStreakTypeAndRoutineIsNull(
            Long memberId, StreakType streakType);

    Optional<Streak> findByMemberIdAndStreakTypeAndRoutineId(
            Long memberId, StreakType streakType, Long routineId);

    List<Streak> findByMemberIdAndStreakType(
            Long memberId, StreakType streakType);
}
