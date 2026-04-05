package ds.project.orino.domain.calendar.repository;

import ds.project.orino.domain.calendar.entity.ScheduleBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleBlockRepository
        extends JpaRepository<ScheduleBlock, Long> {

    List<ScheduleBlock> findByDailyScheduleIdOrderBySortOrder(
            Long dailyScheduleId);

    @Query("SELECT b FROM ScheduleBlock b "
            + "WHERE b.dailySchedule.member.id = :memberId "
            + "AND b.dailySchedule.scheduleDate BETWEEN :fromDate AND :toDate")
    List<ScheduleBlock> findByMemberIdAndDateBetween(
            @Param("memberId") Long memberId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
