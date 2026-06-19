package ds.project.orino.planner.google.recurrence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecurrenceRuleTest {

    @Test
    @DisplayName("freq가 null이면 예외")
    void freqRequired() {
        assertThatThrownBy(() -> new RecurrenceRule(null, null, List.of(), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("interval은 1 미만이면 예외")
    void intervalPositive() {
        assertThatThrownBy(() ->
                new RecurrenceRule(RecurrenceFreq.DAILY, 0, List.of(), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("byMonthDay는 1~31 범위를 벗어나면 예외")
    void byMonthDayRange() {
        assertThatThrownBy(() ->
                new RecurrenceRule(RecurrenceFreq.MONTHLY, null, List.of(), List.of(32), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                new RecurrenceRule(RecurrenceFreq.MONTHLY, null, List.of(), List.of(0), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 목록은 빈 불변 리스트로 정규화된다")
    void nullListsNormalized() {
        RecurrenceRule rule = new RecurrenceRule(RecurrenceFreq.DAILY, null, null, null, null);

        assertThat(rule.byDay()).isEmpty();
        assertThat(rule.byMonthDay()).isEmpty();
    }

    @Test
    @DisplayName("목록은 방어적 복사된다 (원본 변경 영향 없음)")
    void defensiveCopy() {
        List<Integer> source = Arrays.asList(1, 15);
        RecurrenceRule rule = RecurrenceRule.monthly(source, null);
        source.set(0, 99);

        assertThat(rule.byMonthDay()).containsExactly(1, 15);
    }

    @Test
    @DisplayName("effectiveInterval은 null을 1로 본다")
    void effectiveInterval() {
        assertThat(RecurrenceRule.daily(null).effectiveInterval()).isEqualTo(1);
        assertThat(RecurrenceRule.everyNDays(4, null).effectiveInterval()).isEqualTo(4);
    }
}
