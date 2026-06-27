package ds.project.orino.planner.dayplan;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.dayplan.entity.DayPlan;
import ds.project.orino.domain.planner.dayplan.entity.DayPlanFreq;
import ds.project.orino.planner.dayplan.dto.DayPlanRecurrence;
import ds.project.orino.planner.google.recurrence.RecurrenceFreq;
import ds.project.orino.planner.google.recurrence.RecurrenceRule;
import ds.project.orino.planner.google.recurrence.RecurrenceWeekday;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 플랜 반복 규칙 매핑 — DTO/엔티티 컬럼 ↔ {@link RecurrenceRule}(앱 계층 VO, 검증·한글요약·펼침 공용).
 * 엔티티는 {@code orino-domain-rdb}라 RecurrenceRule을 직접 못 들고, 컬럼(freq enum + CSV)으로 저장한다.
 */
@Component
public class DayPlanRecurrenceMapper {

    /** 요청 DTO → RecurrenceRule(생성자에서 interval·byMonthDay 검증). 잘못된 값은 PLN-ERR-002. */
    public RecurrenceRule toRule(DayPlanRecurrence dto) {
        try {
            RecurrenceFreq freq = RecurrenceFreq.valueOf(dto.freq().trim().toUpperCase());
            List<RecurrenceWeekday> byDay = dto.byDay() == null ? List.of()
                    : dto.byDay().stream().map(RecurrenceWeekday::fromCode).toList();
            List<Integer> byMonthDay = dto.byMonthDay() == null ? List.of() : dto.byMonthDay();
            return new RecurrenceRule(freq, dto.interval(), byDay, byMonthDay, dto.until());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.ROUTINE_INVALID_RULE);
        }
    }

    /** 엔티티 컬럼 → RecurrenceRule. */
    public RecurrenceRule toRule(DayPlan plan) {
        RecurrenceFreq freq = RecurrenceFreq.valueOf(plan.getFreq().name());
        return new RecurrenceRule(
                freq, plan.getIntervalVal(),
                parseWeekdays(plan.getByDay()), parseInts(plan.getByMonthDay()), plan.getUntil());
    }

    /** RecurrenceRule → 응답 DTO(정규화된 값 그대로). */
    public DayPlanRecurrence toDto(RecurrenceRule rule, LocalDate startsOn) {
        return new DayPlanRecurrence(
                rule.freq().name(),
                rule.interval(),
                rule.byDay().isEmpty() ? null : rule.byDay().stream().map(Enum::name).toList(),
                rule.byMonthDay().isEmpty() ? null : List.copyOf(rule.byMonthDay()),
                startsOn,
                rule.until());
    }

    /** WEEKLY일 때만 요일 CSV, 그 외 null. */
    public String byDayColumn(RecurrenceRule rule) {
        if (rule.freq() != RecurrenceFreq.WEEKLY || rule.byDay().isEmpty()) {
            return null;
        }
        return rule.byDay().stream().map(Enum::name).collect(Collectors.joining(","));
    }

    /** MONTHLY일 때만 일자 CSV, 그 외 null. */
    public String byMonthDayColumn(RecurrenceRule rule) {
        if (rule.freq() != RecurrenceFreq.MONTHLY || rule.byMonthDay().isEmpty()) {
            return null;
        }
        return rule.byMonthDay().stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public DayPlanFreq freqColumn(RecurrenceRule rule) {
        return DayPlanFreq.valueOf(rule.freq().name());
    }

    private List<RecurrenceWeekday> parseWeekdays(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(RecurrenceWeekday::fromCode).toList();
    }

    private List<Integer> parseInts(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).map(Integer::valueOf).toList();
    }
}
