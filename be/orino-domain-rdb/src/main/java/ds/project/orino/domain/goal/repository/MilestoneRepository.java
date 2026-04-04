package ds.project.orino.domain.goal.repository;

import ds.project.orino.domain.goal.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    Optional<Milestone> findByIdAndGoalId(Long id, Long goalId);
}
