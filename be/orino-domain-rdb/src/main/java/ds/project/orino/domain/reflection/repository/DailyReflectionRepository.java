package ds.project.orino.domain.reflection.repository;

import ds.project.orino.domain.reflection.entity.DailyReflection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyReflectionRepository
        extends JpaRepository<DailyReflection, Long> {

    Optional<DailyReflection> findByMemberIdAndReflectionDate(
            Long memberId, LocalDate reflectionDate);
}
