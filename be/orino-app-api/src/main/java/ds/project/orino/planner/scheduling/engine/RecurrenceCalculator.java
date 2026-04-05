package ds.project.orino.planner.scheduling.engine;

import ds.project.orino.domain.fixedschedule.entity.FixedSchedule;
import ds.project.orino.domain.fixedschedule.entity.RecurrenceType;
import ds.project.orino.domain.routine.entity.Routine;
import ds.project.orino.domain.routine.entity.RoutineStatus;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 반복 규칙을 런타임에 계산하여 특정 날짜에 적용되는지 판정한다.
 * fixed_schedule / routine의 반복 패턴을 공통으로 처리한다.
 */
@Component
public class RecurrenceCalculator {

    public boolean applies(FixedSchedule schedule, LocalDate date) {
        RecurrenceType type = schedule.getRecurrenceType();
        if (type == RecurrenceType.NONE) {
            return date.equals(schedule.getScheduleDate());
        }
        if (!withinRange(date,
                schedule.getRecurrenceStart(),
                schedule.getRecurrenceEnd())) {
            return false;
        }
        return matchesPattern(type,
                schedule.getRecurrenceInterval(),
                schedule.getRecurrenceDays(),
                schedule.getRecurrenceStart(), date);
    }

    public boolean applies(Routine routine, LocalDate date) {
        if (routine.getStatus() != RoutineStatus.ACTIVE) {
            return false;
        }
        if (!withinRange(date,
                routine.getStartDate(),
                routine.getEndDate())) {
            return false;
        }
        if (routine.isSkipHolidays() && isWeekend(date)) {
            return false;
        }
        boolean excluded = routine.getExceptions().stream()
                .anyMatch(e -> e.getExceptionDate() != null
                        && e.getExceptionDate().equals(date));
        if (excluded) {
            return false;
        }
        return matchesPattern(routine.getRecurrenceType(),
                routine.getRecurrenceInterval(),
                routine.getRecurrenceDays(),
                routine.getStartDate(), date);
    }

    private boolean withinRange(LocalDate date, LocalDate start,
                                LocalDate end) {
        if (start != null && date.isBefore(start)) {
            return false;
        }
        if (end != null && date.isAfter(end)) {
            return false;
        }
        return true;
    }

    private boolean matchesPattern(RecurrenceType type, Integer interval,
                                   String days, LocalDate anchor,
                                   LocalDate date) {
        return switch (type) {
            case NONE -> false;
            case DAILY -> true;
            case EVERY_N_DAYS -> matchesEveryNDays(interval, anchor, date);
            case WEEKLY -> matchesWeekly(days, date);
            case MONTHLY_DATE -> matchesMonthlyDate(days, date);
            case MONTHLY_NTH_DAY -> matchesMonthlyNthDay(days, date);
        };
    }

    private boolean matchesEveryNDays(Integer interval, LocalDate anchor,
                                      LocalDate date) {
        if (interval == null || interval <= 0 || anchor == null) {
            return false;
        }
        long diff = ChronoUnit.DAYS.between(anchor, date);
        return diff >= 0 && diff % interval == 0;
    }

    private boolean matchesWeekly(String days, LocalDate date) {
        if (days == null || days.isBlank()) {
            return false;
        }
        Set<DayOfWeek> allowed = Arrays.stream(days.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::parseDayOfWeek)
                .collect(Collectors.toSet());
        return allowed.contains(date.getDayOfWeek());
    }

    private boolean matchesMonthlyDate(String days, LocalDate date) {
        if (days == null || days.isBlank()) {
            return false;
        }
        int dayOfMonth = date.getDayOfMonth();
        return Arrays.stream(days.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .anyMatch(d -> d == dayOfMonth);
    }

    private boolean matchesMonthlyNthDay(String days, LocalDate date) {
        if (days == null || days.isBlank()) {
            return false;
        }
        for (String token : days.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            String[] parts = t.split("-");
            if (parts.length != 2) {
                continue;
            }
            int nth = Integer.parseInt(parts[0].trim());
            DayOfWeek dow = parseDayOfWeek(parts[1].trim());
            if (date.getDayOfWeek() == dow
                    && nthOccurrenceInMonth(date) == nth) {
                return true;
            }
        }
        return false;
    }

    private int nthOccurrenceInMonth(LocalDate date) {
        return (date.getDayOfMonth() - 1) / 7 + 1;
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek d = date.getDayOfWeek();
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }

    private DayOfWeek parseDayOfWeek(String token) {
        return switch (token.toUpperCase()) {
            case "MON" -> DayOfWeek.MONDAY;
            case "TUE" -> DayOfWeek.TUESDAY;
            case "WED" -> DayOfWeek.WEDNESDAY;
            case "THU" -> DayOfWeek.THURSDAY;
            case "FRI" -> DayOfWeek.FRIDAY;
            case "SAT" -> DayOfWeek.SATURDAY;
            case "SUN" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException(
                    "알 수 없는 요일: " + token);
        };
    }
}
