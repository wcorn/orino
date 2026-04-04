package ds.project.orino.domain.routine.repository;

import ds.project.orino.domain.routine.entity.Routine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoutineRepository extends JpaRepository<Routine, Long> {

    List<Routine> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    Optional<Routine> findByIdAndMemberId(Long id, Long memberId);
}
