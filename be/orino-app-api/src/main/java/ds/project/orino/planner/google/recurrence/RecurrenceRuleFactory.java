package ds.project.orino.planner.google.recurrence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link RecurrenceRule} VO ↔ Google Calendar RRULE 문자열 변환기.
 *
 * <p>{@code RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;UNTIL=20261231T145959Z} 형태를 생성·역파싱한다.
 * {@code UNTIL}은 RFC 5545에 따라 사용자 시간대의 마지막 날 23:59:59를 UTC로 변환해 직렬화한다.
 */
public final class RecurrenceRuleFactory {

    private static final String RRULE_PREFIX = "RRULE:";
    /** UNTIL UTC 직렬화 포맷: 20261231T235959Z. */
    private static final DateTimeFormatter UNTIL_UTC =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter UNTIL_DATE_ONLY =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private RecurrenceRuleFactory() {
    }

    /**
     * VO를 RRULE 문자열로 직렬화한다(접두사 {@code RRULE:} 포함).
     *
     * @param zone {@code UNTIL}을 UTC로 변환할 때 기준이 되는 사용자 시간대
     */
    public static String toRRule(RecurrenceRule rule, ZoneId zone) {
        StringBuilder sb = new StringBuilder(RRULE_PREFIX);
        sb.append("FREQ=").append(rule.freq().name());

        if (rule.effectiveInterval() > 1) {
            sb.append(";INTERVAL=").append(rule.effectiveInterval());
        }
        if (!rule.byDay().isEmpty()) {
            sb.append(";BYDAY=").append(joinByDay(rule.byDay()));
        }
        if (!rule.byMonthDay().isEmpty()) {
            sb.append(";BYMONTHDAY=").append(joinByMonthDay(rule.byMonthDay()));
        }
        if (rule.until() != null) {
            sb.append(";UNTIL=").append(serializeUntil(rule.until(), zone));
        }
        return sb.toString();
    }

    /**
     * RRULE 문자열을 VO로 역파싱한다. 접두사 {@code RRULE:}는 있어도/없어도 된다.
     * 시리즈 편집 시 폼을 채우기 위한 용도.
     *
     * @param zone {@code UNTIL}(UTC)을 사용자 시간대 날짜로 환산할 때 기준이 되는 시간대
     */
    public static RecurrenceRule parse(String rrule, ZoneId zone) {
        if (rrule == null || rrule.isBlank()) {
            throw new IllegalArgumentException("RRULE 문자열이 비어 있습니다");
        }
        String body = rrule.trim();
        if (body.regionMatches(true, 0, RRULE_PREFIX, 0, RRULE_PREFIX.length())) {
            body = body.substring(RRULE_PREFIX.length());
        }

        RecurrenceFreq freq = null;
        Integer interval = null;
        List<RecurrenceWeekday> byDay = List.of();
        List<Integer> byMonthDay = List.of();
        LocalDate until = null;

        for (String part : body.split(";")) {
            if (part.isBlank()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = part.substring(0, eq).trim().toUpperCase();
            String value = part.substring(eq + 1).trim();
            switch (key) {
                case "FREQ" -> freq = RecurrenceFreq.valueOf(value.toUpperCase());
                case "INTERVAL" -> interval = Integer.valueOf(value);
                case "BYDAY" -> byDay = parseByDay(value);
                case "BYMONTHDAY" -> byMonthDay = parseByMonthDay(value);
                case "UNTIL" -> until = parseUntil(value, zone);
                default -> {
                    // BYSETPOS 등 미지원 파트는 무시한다(Phase 1)
                }
            }
        }
        if (freq == null) {
            throw new IllegalArgumentException("RRULE에 FREQ가 없습니다: " + rrule);
        }
        return new RecurrenceRule(freq, interval, byDay, byMonthDay, until);
    }

    private static String joinByDay(List<RecurrenceWeekday> byDay) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < byDay.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(byDay.get(i).name());
        }
        return sb.toString();
    }

    private static String joinByMonthDay(List<Integer> byMonthDay) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < byMonthDay.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(byMonthDay.get(i));
        }
        return sb.toString();
    }

    private static List<RecurrenceWeekday> parseByDay(String value) {
        List<RecurrenceWeekday> days = new ArrayList<>();
        for (String code : value.split(",")) {
            if (!code.isBlank()) {
                days.add(RecurrenceWeekday.fromCode(code));
            }
        }
        return days;
    }

    private static List<Integer> parseByMonthDay(String value) {
        List<Integer> days = new ArrayList<>();
        for (String day : value.split(",")) {
            if (!day.isBlank()) {
                days.add(Integer.valueOf(day.trim()));
            }
        }
        return days;
    }

    /** 사용자 TZ 마지막 날 23:59:59 → UTC 직렬화. */
    private static String serializeUntil(LocalDate until, ZoneId zone) {
        return until.atTime(LocalTime.of(23, 59, 59))
                .atZone(zone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .format(UNTIL_UTC);
    }

    /** UNTIL 값을 사용자 TZ 날짜로 환산. UTC datetime(...Z) 또는 date-only(yyyyMMdd) 모두 수용. */
    private static LocalDate parseUntil(String value, ZoneId zone) {
        String v = value.trim();
        if (v.endsWith("Z") || v.endsWith("z")) {
            LocalDateTime utc = LocalDateTime.parse(v.substring(0, v.length() - 1), UNTIL_UTC_PARSE);
            return utc.atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDate();
        }
        return LocalDate.parse(v, UNTIL_DATE_ONLY);
    }

    /** UTC datetime 파싱용(말미 Z 제외): yyyyMMdd'T'HHmmss. */
    private static final DateTimeFormatter UNTIL_UTC_PARSE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
}
