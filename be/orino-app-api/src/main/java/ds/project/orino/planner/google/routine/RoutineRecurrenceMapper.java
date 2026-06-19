package ds.project.orino.planner.google.routine;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.planner.google.recurrence.RecurrenceFreq;
import ds.project.orino.planner.google.recurrence.RecurrenceRule;
import ds.project.orino.planner.google.recurrence.RecurrenceWeekday;
import ds.project.orino.planner.google.routine.dto.RoutineRecurrence;

import java.util.List;

/**
 * 요청·응답 {@link RoutineRecurrence} DTO ↔ {@link RecurrenceRule} VO 변환.
 * 잘못된 값은 {@link ErrorCode#ROUTINE_INVALID_RULE}(400)로 변환한다.
 */
public final class RoutineRecurrenceMapper {

    private RoutineRecurrenceMapper() {
    }

    /** 요청 DTO → VO. freq/요일 코드/일자 범위가 잘못되면 ROUTINE_INVALID_RULE. */
    public static RecurrenceRule toRule(RoutineRecurrence dto) {
        try {
            RecurrenceFreq freq = RecurrenceFreq.valueOf(dto.freq().trim().toUpperCase());
            List<RecurrenceWeekday> byDay = dto.byDay() == null
                    ? List.of()
                    : dto.byDay().stream().map(RecurrenceWeekday::fromCode).toList();
            List<Integer> byMonthDay = dto.byMonthDay() == null ? List.of() : dto.byMonthDay();
            return new RecurrenceRule(freq, dto.interval(), byDay, byMonthDay, dto.until());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CustomException(ErrorCode.ROUTINE_INVALID_RULE, e);
        }
    }

    /** VO → 응답 DTO(정규화된 형태). */
    public static RoutineRecurrence toDto(RecurrenceRule rule) {
        List<String> byDay = rule.byDay().isEmpty()
                ? null
                : rule.byDay().stream().map(Enum::name).toList();
        List<Integer> byMonthDay = rule.byMonthDay().isEmpty() ? null : rule.byMonthDay();
        Integer interval = rule.effectiveInterval() == 1 ? null : rule.effectiveInterval();
        return new RoutineRecurrence(rule.freq().name(), interval, byDay, byMonthDay, rule.until());
    }
}
