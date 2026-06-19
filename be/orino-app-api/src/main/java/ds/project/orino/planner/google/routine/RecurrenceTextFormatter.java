package ds.project.orino.planner.google.routine;

import ds.project.orino.planner.google.recurrence.RecurrenceRule;
import ds.project.orino.planner.google.recurrence.RecurrenceWeekday;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link RecurrenceRule}을 한국어 표시 문구로 변환한다(예: "매주 월·수·금", "3일마다", "매월 1·15일").
 */
public final class RecurrenceTextFormatter {

    private static final Map<RecurrenceWeekday, String> WEEKDAY_KO = Map.of(
            RecurrenceWeekday.MO, "월",
            RecurrenceWeekday.TU, "화",
            RecurrenceWeekday.WE, "수",
            RecurrenceWeekday.TH, "목",
            RecurrenceWeekday.FR, "금",
            RecurrenceWeekday.SA, "토",
            RecurrenceWeekday.SU, "일");

    private RecurrenceTextFormatter() {
    }

    public static String toKorean(RecurrenceRule rule) {
        int interval = rule.effectiveInterval();
        return switch (rule.freq()) {
            case DAILY -> interval == 1 ? "매일" : interval + "일마다";
            case WEEKLY -> weekly(rule, interval);
            case MONTHLY -> monthly(rule, interval);
        };
    }

    private static String weekly(RecurrenceRule rule, int interval) {
        String prefix = interval == 1 ? "매주" : interval + "주마다";
        if (rule.byDay().isEmpty()) {
            return prefix;
        }
        String days = rule.byDay().stream()
                .map(WEEKDAY_KO::get)
                .collect(Collectors.joining("·"));
        return prefix + " " + days;
    }

    private static String monthly(RecurrenceRule rule, int interval) {
        String prefix = interval == 1 ? "매월" : interval + "개월마다";
        List<Integer> days = rule.byMonthDay();
        if (days.isEmpty()) {
            return prefix;
        }
        String dayText = days.stream().map(String::valueOf).collect(Collectors.joining("·"));
        return prefix + " " + dayText + "일";
    }
}
