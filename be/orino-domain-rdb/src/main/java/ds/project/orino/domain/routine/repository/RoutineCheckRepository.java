package ds.project.orino.domain.routine.repository;

import ds.project.orino.domain.routine.entity.RoutineCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface RoutineCheckRepository extends JpaRepository<RoutineCheck, Long> {

    Optional<RoutineCheck> findByRoutineIdAndCheckDate(Long routineId,
                                                       LocalDate checkDate);
}
