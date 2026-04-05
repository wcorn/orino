package ds.project.orino.domain.calendar.repository;

import ds.project.orino.domain.calendar.entity.DailySchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyScheduleRepository
        extends JpaRepository<DailySchedule, Long> {

    Optional<DailySchedule> findByMemberIdAndScheduleDate(
            Long memberId, LocalDate scheduleDate);

    List<DailySchedule> findByMemberIdAndScheduleDateBetween(
            Long memberId, LocalDate from, LocalDate to);
}
