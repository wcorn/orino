package ds.project.orino.domain.goal.repository;

import ds.project.orino.domain.goal.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    Optional<Goal> findByIdAndMemberId(Long id, Long memberId);
}
