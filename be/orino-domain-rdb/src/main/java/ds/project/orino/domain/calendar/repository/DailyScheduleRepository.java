package ds.project.orino.domain.calendar.repository;

import ds.project.orino.domain.calendar.entity.DailySchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyScheduleRepository
        extends JpaRepository<DailySchedule, Long> {

    Optional<DailySchedule> findByMemberIdAndScheduleDate(
            Long memberId, LocalDate scheduleDate);

    List<DailySchedule> findByMemberIdAndScheduleDateBetween(
            Long memberId, LocalDate from, LocalDate to);

    @Modifying
    @Query("UPDATE DailySchedule ds SET ds.dirty = true "
            + "WHERE ds.member.id = :memberId "
            + "AND ds.scheduleDate >= :fromDate")
    int markDirtyFromDate(@Param("memberId") Long memberId,
                          @Param("fromDate") LocalDate fromDate);

    @Modifying
    @Query("UPDATE DailySchedule ds SET ds.dirty = true "
            + "WHERE ds.member.id = :memberId "
            + "AND ds.scheduleDate = :date")
    int markDirtyByDate(@Param("memberId") Long memberId,
                        @Param("date") LocalDate date);
}
