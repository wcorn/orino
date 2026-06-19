package ds.project.orino.planner.google.recurrence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecurrenceRuleFactoryTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");      // UTC+9
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York"); // 12월 UTC-5

    @Nested
    @DisplayName("toRRule: VO → RRULE 직렬화")
    class ToRRule {

        @Test
        @DisplayName("매일 반복은 FREQ=DAILY만 출력한다")
        void daily() {
            String rrule = RecurrenceRuleFactory.toRRule(RecurrenceRule.daily(null), SEOUL);

            assertThat(rrule).isEqualTo("RRULE:FREQ=DAILY");
        }

        @Test
        @DisplayName("N일 간격은 INTERVAL=N을 붙이고, interval=1은 생략한다")
        void interval() {
            assertThat(RecurrenceRuleFactory.toRRule(RecurrenceRule.everyNDays(3, null), SEOUL))
                    .isEqualTo("RRULE:FREQ=DAILY;INTERVAL=3");
            assertThat(RecurrenceRuleFactory.toRRule(RecurrenceRule.everyNDays(1, null), SEOUL))
                    .isEqualTo("RRULE:FREQ=DAILY");
        }

        @Test
        @DisplayName("주간 반복은 BYDAY를 요일 순서대로 출력한다")
        void weeklyByDay() {
            RecurrenceRule rule = RecurrenceRule.weekly(
                    List.of(RecurrenceWeekday.MO, RecurrenceWeekday.WE, RecurrenceWeekday.FR), null);

            assertThat(RecurrenceRuleFactory.toRRule(rule, SEOUL))
                    .isEqualTo("RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR");
        }

        @Test
        @DisplayName("매월 반복은 BYMONTHDAY를 출력한다")
        void monthlyByMonthDay() {
            RecurrenceRule rule = RecurrenceRule.monthly(List.of(1, 15), null);

            assertThat(RecurrenceRuleFactory.toRRule(rule, SEOUL))
                    .isEqualTo("RRULE:FREQ=MONTHLY;BYMONTHDAY=1,15");
        }

        @Test
        @DisplayName("UNTIL은 사용자 TZ 마지막날 23:59:59를 UTC로 변환한다 (KST → -9h)")
        void untilUtcSeoul() {
            RecurrenceRule rule = RecurrenceRule.daily(LocalDate.of(2026, 12, 31));

            assertThat(RecurrenceRuleFactory.toRRule(rule, SEOUL))
                    .isEqualTo("RRULE:FREQ=DAILY;UNTIL=20261231T145959Z");
        }

        @Test
        @DisplayName("UNTIL UTC 변환이 날짜를 넘기는 경우(EST → +5h, 익일 롤오버)")
        void untilUtcRollover() {
            RecurrenceRule rule = RecurrenceRule.daily(LocalDate.of(2026, 12, 31));

            assertThat(RecurrenceRuleFactory.toRRule(rule, NEW_YORK))
                    .isEqualTo("RRULE:FREQ=DAILY;UNTIL=20270101T045959Z");
        }

        @Test
        @DisplayName("모든 파트를 함께 출력한다 (간격+요일+UNTIL)")
        void combined() {
            RecurrenceRule rule = new RecurrenceRule(
                    RecurrenceFreq.WEEKLY, 2,
                    List.of(RecurrenceWeekday.TU, RecurrenceWeekday.TH),
                    List.of(),
                    LocalDate.of(2026, 12, 31));

            assertThat(RecurrenceRuleFactory.toRRule(rule, SEOUL))
                    .isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH;UNTIL=20261231T145959Z");
        }
    }

    @Nested
    @DisplayName("parse: RRULE → VO 역파싱")
    class Parse {

        @Test
        @DisplayName("RRULE: 접두사 유무 모두 수용한다")
        void prefixOptional() {
            assertThat(RecurrenceRuleFactory.parse("RRULE:FREQ=DAILY", SEOUL).freq())
                    .isEqualTo(RecurrenceFreq.DAILY);
            assertThat(RecurrenceRuleFactory.parse("FREQ=DAILY", SEOUL).freq())
                    .isEqualTo(RecurrenceFreq.DAILY);
        }

        @Test
        @DisplayName("INTERVAL/BYDAY/BYMONTHDAY를 파싱한다")
        void parts() {
            RecurrenceRule weekly = RecurrenceRuleFactory.parse(
                    "RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE,FR", SEOUL);
            assertThat(weekly.freq()).isEqualTo(RecurrenceFreq.WEEKLY);
            assertThat(weekly.interval()).isEqualTo(2);
            assertThat(weekly.byDay())
                    .containsExactly(RecurrenceWeekday.MO, RecurrenceWeekday.WE, RecurrenceWeekday.FR);

            RecurrenceRule monthly = RecurrenceRuleFactory.parse(
                    "RRULE:FREQ=MONTHLY;BYMONTHDAY=1,15", SEOUL);
            assertThat(monthly.byMonthDay()).containsExactly(1, 15);
        }

        @Test
        @DisplayName("UNTIL(UTC)을 사용자 TZ 날짜로 환산한다")
        void untilToLocalDate() {
            RecurrenceRule rule = RecurrenceRuleFactory.parse(
                    "RRULE:FREQ=DAILY;UNTIL=20261231T145959Z", SEOUL);

            assertThat(rule.until()).isEqualTo(LocalDate.of(2026, 12, 31));
        }

        @Test
        @DisplayName("UNTIL UTC가 익일 04:59:59Z여도 EST 기준 12/31로 환산한다")
        void untilRollbackToLocalDate() {
            RecurrenceRule rule = RecurrenceRuleFactory.parse(
                    "RRULE:FREQ=DAILY;UNTIL=20270101T045959Z", NEW_YORK);

            assertThat(rule.until()).isEqualTo(LocalDate.of(2026, 12, 31));
        }

        @Test
        @DisplayName("date-only UNTIL(yyyyMMdd)도 수용한다")
        void untilDateOnly() {
            RecurrenceRule rule = RecurrenceRuleFactory.parse(
                    "RRULE:FREQ=DAILY;UNTIL=20261231", SEOUL);

            assertThat(rule.until()).isEqualTo(LocalDate.of(2026, 12, 31));
        }

        @Test
        @DisplayName("미지원 파트(BYSETPOS 등)는 무시한다")
        void ignoresUnsupported() {
            RecurrenceRule rule = RecurrenceRuleFactory.parse(
                    "RRULE:FREQ=MONTHLY;BYSETPOS=1;BYDAY=MO", SEOUL);

            assertThat(rule.freq()).isEqualTo(RecurrenceFreq.MONTHLY);
            assertThat(rule.byDay()).containsExactly(RecurrenceWeekday.MO);
        }

        @Test
        @DisplayName("FREQ가 없으면 예외")
        void missingFreq() {
            assertThatThrownBy(() -> RecurrenceRuleFactory.parse("RRULE:INTERVAL=2", SEOUL))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("빈 문자열이면 예외")
        void blank() {
            assertThatThrownBy(() -> RecurrenceRuleFactory.parse("  ", SEOUL))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("round-trip: toRRule ↔ parse")
    class RoundTrip {

        @Test
        @DisplayName("주간+간격+UNTIL을 직렬화 후 역파싱하면 동등하다")
        void weeklyRoundTrip() {
            RecurrenceRule original = new RecurrenceRule(
                    RecurrenceFreq.WEEKLY, 2,
                    List.of(RecurrenceWeekday.MO, RecurrenceWeekday.FR),
                    List.of(),
                    LocalDate.of(2026, 12, 31));

            String rrule = RecurrenceRuleFactory.toRRule(original, SEOUL);
            RecurrenceRule parsed = RecurrenceRuleFactory.parse(rrule, SEOUL);

            assertThat(parsed.freq()).isEqualTo(original.freq());
            assertThat(parsed.effectiveInterval()).isEqualTo(original.effectiveInterval());
            assertThat(parsed.byDay()).isEqualTo(original.byDay());
            assertThat(parsed.until()).isEqualTo(original.until());
        }
    }
}
