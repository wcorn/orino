package ds.project.orino.planner.scheduling.dirty;

import ds.project.orino.domain.calendar.repository.DailyScheduleRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 스케줄에 영향을 주는 데이터가 변경되었을 때 해당 사용자의 DailySchedule을
 * dirty 마킹한다. 이후 스케줄 조회 시 SchedulingEngine이 lazy 재생성한다.
 */
@Component
public class DirtyScheduleMarker {

    private final DailyScheduleRepository dailyScheduleRepository;

    public DirtyScheduleMarker(DailyScheduleRepository dailyScheduleRepository) {
        this.dailyScheduleRepository = dailyScheduleRepository;
    }

    /**
     * 오늘부터 미래의 모든 DailySchedule을 dirty 마킹한다.
     * (루틴/고정 일정/설정 변경 등 반복/지속적인 변경에 사용)
     */
    @Transactional
    public void markDirtyFromToday(Long memberId) {
        markDirtyFrom(memberId, LocalDate.now());
    }

    /**
     * 지정한 날짜부터 미래의 모든 DailySchedule을 dirty 마킹한다.
     */
    @Transactional
    public void markDirtyFrom(Long memberId, LocalDate fromDate) {
        dailyScheduleRepository.markDirtyFromDate(memberId, fromDate);
    }

    /**
     * 특정 날짜의 DailySchedule만 dirty 마킹한다.
     */
    @Transactional
    public void markDirtyOn(Long memberId, LocalDate date) {
        dailyScheduleRepository.markDirtyByDate(memberId, date);
    }
}
