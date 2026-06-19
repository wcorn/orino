package ds.project.orino.planner.google.routine;

import ds.project.orino.planner.google.recurrence.RecurrenceFreq;
import ds.project.orino.planner.google.recurrence.RecurrenceRule;
import ds.project.orino.planner.google.recurrence.RecurrenceWeekday;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecurrenceTextFormatterTest {

    @Test
    @DisplayName("매일 / N일마다")
    void daily() {
        assertThat(RecurrenceTextFormatter.toKorean(RecurrenceRule.daily(null))).isEqualTo("매일");
        assertThat(RecurrenceTextFormatter.toKorean(RecurrenceRule.everyNDays(3, null)))
                .isEqualTo("3일마다");
    }

    @Test
    @DisplayName("매주 요일 / N주마다 요일")
    void weekly() {
        RecurrenceRule weekly = RecurrenceRule.weekly(
                List.of(RecurrenceWeekday.MO, RecurrenceWeekday.WE, RecurrenceWeekday.FR), null);
        assertThat(RecurrenceTextFormatter.toKorean(weekly)).isEqualTo("매주 월·수·금");

        RecurrenceRule biweekly = new RecurrenceRule(
                RecurrenceFreq.WEEKLY, 2, List.of(RecurrenceWeekday.TU), List.of(), null);
        assertThat(RecurrenceTextFormatter.toKorean(biweekly)).isEqualTo("2주마다 화");
    }

    @Test
    @DisplayName("매월 일자 / N개월마다")
    void monthly() {
        assertThat(RecurrenceTextFormatter.toKorean(RecurrenceRule.monthly(List.of(1, 15), null)))
                .isEqualTo("매월 1·15일");

        RecurrenceRule quarterly = new RecurrenceRule(
                RecurrenceFreq.MONTHLY, 3, List.of(), List.of(1), null);
        assertThat(RecurrenceTextFormatter.toKorean(quarterly)).isEqualTo("3개월마다 1일");
    }
}
