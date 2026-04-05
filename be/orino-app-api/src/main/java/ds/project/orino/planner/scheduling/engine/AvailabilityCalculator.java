package ds.project.orino.planner.scheduling.engine;

import ds.project.orino.domain.calendar.entity.BlockType;
import ds.project.orino.domain.fixedschedule.entity.FixedSchedule;
import ds.project.orino.domain.fixedschedule.repository.FixedScheduleRepository;
import ds.project.orino.domain.preference.entity.UserPreference;
import ds.project.orino.domain.routine.entity.Routine;
import ds.project.orino.domain.routine.repository.RoutineRepository;
import ds.project.orino.planner.scheduling.engine.model.AvailabilityResult;
import ds.project.orino.planner.scheduling.engine.model.PlacedBlock;
import ds.project.orino.planner.scheduling.engine.model.TimeSlot;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 하루의 자유 시간 블록을 계산한다.
 * wake~sleep 윈도우에서 고정 일정과 루틴이 점유하는 시간을 차감한다.
 */
@Component
public class AvailabilityCalculator {

    /** 취침 시각이 자정(00:00)인 경우 하루 끝 시각으로 사용한다. */
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59);

    private final FixedScheduleRepository fixedScheduleRepository;
    private final RoutineRepository routineRepository;
    private final RecurrenceCalculator recurrenceCalculator;

    public AvailabilityCalculator(
            FixedScheduleRepository fixedScheduleRepository,
            RoutineRepository routineRepository,
            RecurrenceCalculator recurrenceCalculator) {
        this.fixedScheduleRepository = fixedScheduleRepository;
        this.routineRepository = routineRepository;
        this.recurrenceCalculator = recurrenceCalculator;
    }

    public AvailabilityResult calculate(Long memberId, LocalDate date,
                                        UserPreference preference) {
        LocalTime wake = preference.getWakeTime();
        LocalTime sleep = resolveSleepTime(preference.getSleepTime(), wake);
        TimeSlot window = new TimeSlot(wake, sleep);

        List<PlacedBlock> preplaced = new ArrayList<>();

        // 1. 고정 일정 배치
        List<FixedSchedule> fixedSchedules = fixedScheduleRepository
                .findByMemberIdOrderByStartTime(memberId);
        for (FixedSchedule fs : fixedSchedules) {
            if (!recurrenceCalculator.applies(fs, date)) {
                continue;
            }
            TimeSlot slot = intersect(window,
                    new TimeSlot(fs.getStartTime(), fs.getEndTime()));
            if (slot == null) {
                continue;
            }
            preplaced.add(new PlacedBlock(
                    BlockType.FIXED, fs.getId(),
                    slot.start(), slot.end(), fs.getTitle()));
        }

        // 2. 루틴 배치
        List<Routine> routines = routineRepository
                .findByMemberIdOrderByCreatedAtDesc(memberId);
        LocalTime cursor = wake;
        for (Routine routine : routines) {
            if (!recurrenceCalculator.applies(routine, date)) {
                continue;
            }
            PlacedBlock placed = placeRoutine(routine, window,
                    preplaced, cursor);
            if (placed != null) {
                preplaced.add(placed);
                if (routine.getPreferredTime() == null) {
                    cursor = placed.end();
                }
            }
        }

        // 3. preplaced로 window를 쪼개서 free slot 계산
        preplaced.sort(Comparator.comparing(PlacedBlock::start));
        List<TimeSlot> freeSlots = subtract(window, preplaced);

        return new AvailabilityResult(freeSlots, preplaced);
    }

    private PlacedBlock placeRoutine(Routine routine, TimeSlot window,
                                     List<PlacedBlock> preplaced,
                                     LocalTime cursor) {
        int duration = routine.getDurationMinutes();
        LocalTime preferred = routine.getPreferredTime();

        LocalTime start = preferred != null ? preferred : cursor;
        if (start.isBefore(window.start())) {
            start = window.start();
        }
        LocalTime end = start.plusMinutes(duration);
        if (end.isAfter(window.end()) || !start.isBefore(window.end())) {
            return null;
        }
        TimeSlot proposed = new TimeSlot(start, end);
        for (PlacedBlock p : preplaced) {
            TimeSlot occupied = new TimeSlot(p.start(), p.end());
            if (proposed.overlaps(occupied)) {
                return null;
            }
        }
        return new PlacedBlock(BlockType.ROUTINE, routine.getId(),
                start, end, routine.getTitle());
    }

    private TimeSlot intersect(TimeSlot a, TimeSlot b) {
        LocalTime start = a.start().isAfter(b.start())
                ? a.start() : b.start();
        LocalTime end = a.end().isBefore(b.end())
                ? a.end() : b.end();
        if (!start.isBefore(end)) {
            return null;
        }
        return new TimeSlot(start, end);
    }

    private List<TimeSlot> subtract(TimeSlot window,
                                    List<PlacedBlock> taken) {
        List<TimeSlot> result = new ArrayList<>();
        LocalTime cursor = window.start();
        for (PlacedBlock block : taken) {
            if (!block.start().isAfter(cursor)) {
                if (block.end().isAfter(cursor)) {
                    cursor = block.end();
                }
                continue;
            }
            result.add(new TimeSlot(cursor, block.start()));
            cursor = block.end();
        }
        if (cursor.isBefore(window.end())) {
            result.add(new TimeSlot(cursor, window.end()));
        }
        return result;
    }

    private LocalTime resolveSleepTime(LocalTime sleepTime, LocalTime wake) {
        if (sleepTime == null || sleepTime.equals(LocalTime.MIDNIGHT)
                || !sleepTime.isAfter(wake)) {
            return END_OF_DAY;
        }
        return sleepTime;
    }
}
