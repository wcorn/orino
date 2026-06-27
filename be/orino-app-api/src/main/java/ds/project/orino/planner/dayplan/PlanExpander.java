package ds.project.orino.planner.dayplan;

import ds.project.orino.planner.google.recurrence.RecurrenceRule;
import ds.project.orino.planner.google.recurrence.RecurrenceWeekday;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 플랜 반복 규칙을 로컬에서 [from, to] 날짜로 펼친다(Google 무관). Routine과 달리 orino가 진실이라
 * Google API 펼침을 못 쓰므로 직접 매칭한다. Phase 1: DAILY/WEEKLY/MONTHLY + interval + byDay/byMonthDay,
 * {@code startsOn}(DTSTART)·{@code until}(포함) 경계.
 */
public final class PlanExpander {

    private PlanExpander() {
    }

    /**
     * {@code [from, to]}(양끝 포함) 구간에서 규칙에 맞는 발생일 목록을 오름차순으로 반환한다.
     * {@code startsOn} 이전과 {@code rule.until} 이후는 제외한다.
     */
    public static List<LocalDate> occurrences(RecurrenceRule rule, LocalDate startsOn,
                                              LocalDate from, LocalDate to) {
        LocalDate rangeStart = from.isBefore(startsOn) ? startsOn : from;
        LocalDate rangeEnd = (rule.until() != null && rule.until().isBefore(to)) ? rule.until() : to;
        if (rangeStart.isAfter(rangeEnd)) {
            return List.of();
        }

        List<LocalDate> result = new ArrayList<>();
        for (LocalDate date = rangeStart; !date.isAfter(rangeEnd); date = date.plusDays(1)) {
            if (matches(rule, startsOn, date)) {
                result.add(date);
            }
        }
        return result;
    }

    private static boolean matches(RecurrenceRule rule, LocalDate startsOn, LocalDate date) {
        int interval = rule.effectiveInterval();
        return switch (rule.freq()) {
            case DAILY -> ChronoUnit.DAYS.between(startsOn, date) % interval == 0;
            case WEEKLY -> matchesWeekly(rule, startsOn, date, interval);
            case MONTHLY -> matchesMonthly(rule, startsOn, date, interval);
        };
    }

    private static boolean matchesWeekly(RecurrenceRule rule, LocalDate startsOn,
                                         LocalDate date, int interval) {
        Set<DayOfWeek> days = rule.byDay().isEmpty()
                ? Set.of(startsOn.getDayOfWeek())
                : rule.byDay().stream().map(RecurrenceWeekday::toDayOfWeek)
                        .collect(Collectors.toSet());
        if (!days.contains(date.getDayOfWeek())) {
            return false;
        }
        LocalDate startWeek = startsOn.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate dateWeek = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return ChronoUnit.WEEKS.between(startWeek, dateWeek) % interval == 0;
    }

    private static boolean matchesMonthly(RecurrenceRule rule, LocalDate startsOn,
                                          LocalDate date, int interval) {
        Set<Integer> monthDays = rule.byMonthDay().isEmpty()
                ? Set.of(startsOn.getDayOfMonth())
                : new HashSet<>(rule.byMonthDay());
        if (!monthDays.contains(date.getDayOfMonth())) {
            return false;
        }
        long months = ChronoUnit.MONTHS.between(
                startsOn.withDayOfMonth(1), date.withDayOfMonth(1));
        return months % interval == 0;
    }
}
