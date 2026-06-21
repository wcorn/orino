package ds.project.orino.domain.planner.holiday.repository;

import ds.project.orino.domain.planner.holiday.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findByDateBetween(LocalDate from, LocalDate to);

    Optional<Holiday> findByDate(LocalDate date);
}
