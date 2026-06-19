package ds.project.orino.planner.google.recurrence;

import java.time.DayOfWeek;
import java.util.Map;

/**
 * RRULE {@code BYDAY} 요일 코드(MO, TU, WE, TH, FR, SA, SU)와 {@link DayOfWeek} 매핑.
 */
public enum RecurrenceWeekday {
    MO(DayOfWeek.MONDAY),
    TU(DayOfWeek.TUESDAY),
    WE(DayOfWeek.WEDNESDAY),
    TH(DayOfWeek.THURSDAY),
    FR(DayOfWeek.FRIDAY),
    SA(DayOfWeek.SATURDAY),
    SU(DayOfWeek.SUNDAY);

    private static final Map<DayOfWeek, RecurrenceWeekday> BY_DAY_OF_WEEK = Map.of(
            DayOfWeek.MONDAY, MO,
            DayOfWeek.TUESDAY, TU,
            DayOfWeek.WEDNESDAY, WE,
            DayOfWeek.THURSDAY, TH,
            DayOfWeek.FRIDAY, FR,
            DayOfWeek.SATURDAY, SA,
            DayOfWeek.SUNDAY, SU);

    private final DayOfWeek dayOfWeek;

    RecurrenceWeekday(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public DayOfWeek toDayOfWeek() {
        return dayOfWeek;
    }

    public static RecurrenceWeekday from(DayOfWeek dayOfWeek) {
        return BY_DAY_OF_WEEK.get(dayOfWeek);
    }

    /** RRULE 코드 문자열(MO, TU, ...)을 enum으로 파싱한다. 알 수 없으면 IllegalArgumentException. */
    public static RecurrenceWeekday fromCode(String code) {
        return RecurrenceWeekday.valueOf(code.trim().toUpperCase());
    }
}
