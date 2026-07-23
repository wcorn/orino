package ds.project.orino.domain.planner.lifelog.repository;

import ds.project.orino.domain.planner.lifelog.entity.Flow;
import ds.project.orino.domain.planner.lifelog.entity.FlowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlowRepository extends JpaRepository<Flow, Long> {

    Optional<Flow> findByIdAndMemberId(Long id, Long memberId);

    List<Flow> findAllByMemberIdOrderByStartedAtDescIdDesc(Long memberId);

    List<Flow> findAllByMemberIdAndStatusOrderByStartedAtDescIdDesc(Long memberId, FlowStatus status);
}
