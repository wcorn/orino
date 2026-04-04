package ds.project.orino.domain.routine.repository;

import ds.project.orino.domain.routine.entity.RoutineException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoutineExceptionRepository extends JpaRepository<RoutineException, Long> {

    Optional<RoutineException> findByIdAndRoutineId(Long id, Long routineId);
}
