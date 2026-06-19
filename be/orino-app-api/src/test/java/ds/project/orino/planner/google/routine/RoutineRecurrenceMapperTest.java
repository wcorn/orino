package ds.project.orino.planner.google.routine;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.planner.google.recurrence.RecurrenceFreq;
import ds.project.orino.planner.google.recurrence.RecurrenceRule;
import ds.project.orino.planner.google.recurrence.RecurrenceWeekday;
import ds.project.orino.planner.google.routine.dto.RoutineRecurrence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutineRecurrenceMapperTest {

    @Test
    @DisplayName("요청 DTO → VO (주간 요일·간격·until)")
    void toRule() {
        RoutineRecurrence dto = new RoutineRecurrence(
                "WEEKLY", 2, List.of("MO", "FR"), null, LocalDate.of(2026, 12, 31));

        RecurrenceRule rule = RoutineRecurrenceMapper.toRule(dto);

        assertThat(rule.freq()).isEqualTo(RecurrenceFreq.WEEKLY);
        assertThat(rule.interval()).isEqualTo(2);
        assertThat(rule.byDay()).containsExactly(RecurrenceWeekday.MO, RecurrenceWeekday.FR);
        assertThat(rule.until()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("VO → 응답 DTO (interval=1·빈 목록은 null로 정규화)")
    void toDto() {
        RoutineRecurrence dto = RoutineRecurrenceMapper.toDto(RecurrenceRule.daily(null));

        assertThat(dto.freq()).isEqualTo("DAILY");
        assertThat(dto.interval()).isNull();
        assertThat(dto.byDay()).isNull();
        assertThat(dto.byMonthDay()).isNull();
    }

    @Test
    @DisplayName("알 수 없는 freq는 ROUTINE_INVALID_RULE")
    void invalidFreq() {
        RoutineRecurrence dto = new RoutineRecurrence("YEARLY", null, null, null, null);

        assertThatThrownBy(() -> RoutineRecurrenceMapper.toRule(dto))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROUTINE_INVALID_RULE);
    }

    @Test
    @DisplayName("잘못된 요일 코드는 ROUTINE_INVALID_RULE")
    void invalidWeekday() {
        RoutineRecurrence dto = new RoutineRecurrence("WEEKLY", null, List.of("XX"), null, null);

        assertThatThrownBy(() -> RoutineRecurrenceMapper.toRule(dto))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROUTINE_INVALID_RULE);
    }

    @Test
    @DisplayName("byMonthDay 범위 초과는 ROUTINE_INVALID_RULE")
    void invalidMonthDay() {
        RoutineRecurrence dto = new RoutineRecurrence("MONTHLY", null, null, List.of(40), null);

        assertThatThrownBy(() -> RoutineRecurrenceMapper.toRule(dto))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROUTINE_INVALID_RULE);
    }
}
