package ds.project.orino.planner.google.recurrence;

import java.time.LocalDate;
import java.util.List;

/**
 * 반복 규칙 값 객체(VO). Google Calendar의 RRULE 한 줄에 대응한다.
 *
 * <p>orino는 RRULE을 직접 펼치지 않고 이 VO를 {@link RecurrenceRuleFactory}로 RRULE 문자열과 상호 변환한다.
 * Phase 1 지원 범위: {@code FREQ=DAILY|WEEKLY|MONTHLY}, {@code INTERVAL}, {@code BYDAY}, {@code BYMONTHDAY},
 * {@code UNTIL}. {@code BYSETPOS}·복수 RRULE·EXDATE는 미지원.
 *
 * @param freq       반복 주기(필수)
 * @param interval   반복 간격(N). null·1이면 RRULE에서 생략된다. 1 이상
 * @param byDay      주간 반복 요일 목록(WEEKLY에서만 의미). 비어 있으면 생략
 * @param byMonthDay 매월 반복 일자 목록(1~31, MONTHLY에서만 의미). 비어 있으면 생략
 * @param until      종료일(포함). 사용자 시간대 기준 마지막 날. null이면 무한 반복
 */
public record RecurrenceRule(
        RecurrenceFreq freq,
        Integer interval,
        List<RecurrenceWeekday> byDay,
        List<Integer> byMonthDay,
        LocalDate until
) {

    public RecurrenceRule {
        if (freq == null) {
            throw new IllegalArgumentException("freq는 필수입니다");
        }
        if (interval != null && interval < 1) {
            throw new IllegalArgumentException("interval은 1 이상이어야 합니다: " + interval);
        }
        byDay = byDay == null ? List.of() : List.copyOf(byDay);
        byMonthDay = byMonthDay == null ? List.of() : List.copyOf(byMonthDay);
        for (Integer day : byMonthDay) {
            if (day == null || day < 1 || day > 31) {
                throw new IllegalArgumentException("byMonthDay는 1~31이어야 합니다: " + day);
            }
        }
    }

    /** 매일 반복. */
    public static RecurrenceRule daily(LocalDate until) {
        return new RecurrenceRule(RecurrenceFreq.DAILY, null, List.of(), List.of(), until);
    }

    /** N일 간격 반복. */
    public static RecurrenceRule everyNDays(int interval, LocalDate until) {
        return new RecurrenceRule(RecurrenceFreq.DAILY, interval, List.of(), List.of(), until);
    }

    /** 지정 요일 주간 반복. */
    public static RecurrenceRule weekly(List<RecurrenceWeekday> byDay, LocalDate until) {
        return new RecurrenceRule(RecurrenceFreq.WEEKLY, null, byDay, List.of(), until);
    }

    /** 지정 일자 매월 반복. */
    public static RecurrenceRule monthly(List<Integer> byMonthDay, LocalDate until) {
        return new RecurrenceRule(RecurrenceFreq.MONTHLY, null, List.of(), byMonthDay, until);
    }

    /** RRULE에서 생략 가능한 1을 정규화한 실효 간격. */
    public int effectiveInterval() {
        return interval == null ? 1 : interval;
    }
}
