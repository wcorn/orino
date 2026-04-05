package ds.project.orino.domain.calendar.repository;

import ds.project.orino.domain.calendar.entity.ScheduleBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleBlockRepository
        extends JpaRepository<ScheduleBlock, Long> {

    List<ScheduleBlock> findByDailyScheduleIdOrderBySortOrder(
            Long dailyScheduleId);
}
