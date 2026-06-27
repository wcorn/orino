package ds.project.orino.planner.dayplan;

import ds.project.orino.planner.google.recurrence.RecurrenceRule;
import ds.project.orino.planner.google.recurrence.RecurrenceWeekday;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlanExpander — 로컬 반복 펼침")
class PlanExpanderTest {

    private static final LocalDate MON = LocalDate.of(2026, 6, 22); // 월요일

    @Test
    @DisplayName("DAILY interval=1은 구간 전 날짜를 포함한다")
    void dailyEveryDay() {
        List<LocalDate> dates = PlanExpander.occurrences(
                RecurrenceRule.daily(null), MON, MON, MON.plusDays(3));

        assertThat(dates).containsExactly(
                MON, MON.plusDays(1), MON.plusDays(2), MON.plusDays(3));
    }

    @Test
    @DisplayName("DAILY interval=3은 startsOn 기준 3일 간격만 포함한다")
    void dailyEvery3Days() {
        List<LocalDate> dates = PlanExpander.occurrences(
                RecurrenceRule.everyNDays(3, null), MON, MON, MON.plusDays(7));

        assertThat(dates).containsExactly(MON, MON.plusDays(3), MON.plusDays(6));
    }

    @Test
    @DisplayName("WEEKLY 월·수·금만 포함한다")
    void weeklyByDay() {
        RecurrenceRule rule = RecurrenceRule.weekly(
                List.of(RecurrenceWeekday.MO, RecurrenceWeekday.WE, RecurrenceWeekday.FR), null);

        List<LocalDate> dates = PlanExpander.occurrences(rule, MON, MON, MON.plusDays(6));

        assertThat(dates).containsExactly(MON, MON.plusDays(2), MON.plusDays(4)); // 월,수,금
    }

    @Test
    @DisplayName("WEEKLY interval=2는 격주만 포함한다")
    void weeklyBiweekly() {
        RecurrenceRule rule = new RecurrenceRule(
                ds.project.orino.planner.google.recurrence.RecurrenceFreq.WEEKLY,
                2, List.of(RecurrenceWeekday.MO), List.of(), null);

        List<LocalDate> dates = PlanExpander.occurrences(rule, MON, MON, MON.plusWeeks(3));

        assertThat(dates).containsExactly(MON, MON.plusWeeks(2));
    }

    @Test
    @DisplayName("MONTHLY 1·15일만 포함한다")
    void monthlyByMonthDay() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        RecurrenceRule rule = RecurrenceRule.monthly(List.of(1, 15), null);

        List<LocalDate> dates = PlanExpander.occurrences(
                rule, start, start, LocalDate.of(2026, 7, 31));

        assertThat(dates).containsExactly(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15));
    }

    @Test
    @DisplayName("startsOn 이전과 until 이후는 제외한다")
    void respectsBounds() {
        RecurrenceRule rule = RecurrenceRule.daily(MON.plusDays(2)); // until = 수요일

        List<LocalDate> dates = PlanExpander.occurrences(
                rule, MON, MON.minusDays(3), MON.plusDays(10));

        // startsOn(월)~until(수)만
        assertThat(dates).containsExactly(MON, MON.plusDays(1), MON.plusDays(2));
    }

    @Test
    @DisplayName("구간이 startsOn보다 모두 이전이면 비어 있다")
    void emptyWhenRangeBeforeStart() {
        List<LocalDate> dates = PlanExpander.occurrences(
                RecurrenceRule.daily(null), MON, MON.minusDays(10), MON.minusDays(1));

        assertThat(dates).isEmpty();
    }
}
