package ds.project.orino.planner.scheduling.engine;

import ds.project.orino.domain.fixedschedule.entity.FixedSchedule;
import ds.project.orino.domain.fixedschedule.entity.RecurrenceType;
import ds.project.orino.domain.routine.entity.Routine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class RecurrenceCalculatorTest {

    private final RecurrenceCalculator calc = new RecurrenceCalculator();

    @Test
    @DisplayName("NONE 타입은 scheduleDate에만 적용된다")
    void none() {
        LocalDate target = LocalDate.of(2026, 4, 10);
        FixedSchedule fs = new FixedSchedule(
                null, "단발", null,
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                target, RecurrenceType.NONE,
                null, null, null, null);

        assertThat(calc.applies(fs, target)).isTrue();
        assertThat(calc.applies(fs, target.plusDays(1))).isFalse();
    }

    @Test
    @DisplayName("DAILY 타입은 recurrence_start~end 사이 매일 적용된다")
    void daily() {
        FixedSchedule fs = new FixedSchedule(
                null, "매일", null,
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                null, RecurrenceType.DAILY,
                null, null,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30));

        assertThat(calc.applies(fs, LocalDate.of(2026, 4, 15))).isTrue();
        assertThat(calc.applies(fs, LocalDate.of(2026, 3, 31))).isFalse();
        assertThat(calc.applies(fs, LocalDate.of(2026, 5, 1))).isFalse();
    }

    @Test
    @DisplayName("EVERY_N_DAYS는 anchor부터 N일 간격으로 적용된다")
    void everyNDays() {
        FixedSchedule fs = new FixedSchedule(
                null, "격일", null,
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                null, RecurrenceType.EVERY_N_DAYS,
                2, null,
                LocalDate.of(2026, 4, 1), null);

        assertThat(calc.applies(fs, LocalDate.of(2026, 4, 1))).isTrue();
        assertThat(calc.applies(fs, LocalDate.of(2026, 4, 2))).isFalse();
        assertThat(calc.applies(fs, LocalDate.of(2026, 4, 3))).isTrue();
        assertThat(calc.applies(fs, LocalDate.of(2026, 4, 5))).isTrue();
    }

    @Test
    @DisplayName("WEEKLY는 지정된 요일에만 적용된다")
    void weekly() {
        FixedSchedule fs = new FixedSchedule(
                null, "월수금", null,
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                null, RecurrenceType.WEEKLY,
                null, "MON,WED,FRI",
                LocalDate.of(2026, 4, 1), null);

        assertThat(calc.applies(fs, LocalDate.of(2026, 4, 6))).isTrue();
        assertThat(calc.applies(fs, LocalDate.of(2026, 4, 7))).isFalse();
        assertThat(calc.applies(fs, LocalDate.of(2026, 4, 8))).isTrue();
        assertThat(calc.applies(fs, LocalDate.of(2026, 4, 10))).isTrue();
    }

    @Test
    @DisplayName("MONTHLY_DATE는 지정된 날짜에만 적용된다")
    void monthlyDate() {
        FixedSchedule fs = new FixedSchedule(
                null, "1일,15일", null,
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                null, RecurrenceType.MONTHLY_DATE,
                null, "1,15",
                LocalDate.of(2026, 1, 1), null);

        assertThat(calc.applies(fs, LocalDate.of(2026, 4, 1))).isTrue();
        assertThat(calc.applies(fs, LocalDate.of(2026, 4, 15))).isTrue();
        assertThat(calc.applies(fs, LocalDate.of(2026, 4, 16))).isFalse();
    }

    @Test
    @DisplayName("MONTHLY_NTH_DAY는 매월 n번째 요일에만 적용된다")
    void monthlyNthDay() {
        FixedSchedule fs = new FixedSchedule(
                null, "첫째 월요일", null,
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                null, RecurrenceType.MONTHLY_NTH_DAY,
                null, "1-MON",
                LocalDate.of(2026, 1, 1), null);

        assertThat(calc.applies(fs, LocalDate.of(2026, 4, 6))).isTrue();
        assertThat(calc.applies(fs, LocalDate.of(2026, 4, 13))).isFalse();
        assertThat(calc.applies(fs, LocalDate.of(2026, 4, 7))).isFalse();
    }

    @Test
    @DisplayName("recurrence_end 이후에는 적용되지 않는다")
    void endedRecurrence() {
        FixedSchedule fs = new FixedSchedule(
                null, "매일", null,
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                null, RecurrenceType.DAILY,
                null, null,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 10));

        assertThat(calc.applies(fs, LocalDate.of(2026, 4, 10))).isTrue();
        assertThat(calc.applies(fs, LocalDate.of(2026, 4, 11))).isFalse();
    }

    @Test
    @DisplayName("PAUSED 상태의 루틴은 적용되지 않는다")
    void pausedRoutine() {
        Routine routine = new Routine(
                null, "운동", null, 30, LocalTime.of(7, 0),
                RecurrenceType.DAILY, null, null,
                LocalDate.of(2026, 4, 1), null, false);
        routine.changeStatus(
                ds.project.orino.domain.routine.entity.RoutineStatus.PAUSED);

        assertThat(calc.applies(routine, LocalDate.of(2026, 4, 5))).isFalse();
    }

    @Test
    @DisplayName("skipHolidays=true인 루틴은 주말에 적용되지 않는다")
    void skipHolidays() {
        Routine routine = new Routine(
                null, "운동", null, 30, LocalTime.of(7, 0),
                RecurrenceType.DAILY, null, null,
                LocalDate.of(2026, 4, 1), null, true);

        // 2026-04-11 = 토요일, 2026-04-13 = 월요일
        assertThat(calc.applies(routine, LocalDate.of(2026, 4, 11))).isFalse();
        assertThat(calc.applies(routine, LocalDate.of(2026, 4, 13))).isTrue();
    }
}
