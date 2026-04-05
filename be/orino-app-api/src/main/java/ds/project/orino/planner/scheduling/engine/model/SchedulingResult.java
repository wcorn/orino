package ds.project.orino.planner.scheduling.engine.model;

import ds.project.orino.domain.calendar.entity.DailySchedule;

import java.util.List;

/**
 * 스케줄링 엔진 실행 결과.
 */
public record SchedulingResult(
        DailySchedule dailySchedule,
        List<SchedulingWarning> warnings
) {
}
