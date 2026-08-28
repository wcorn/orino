package ds.project.orino.planner.ledger.recurring;

import ds.project.orino.domain.planner.ledger.entity.LedgerRecurring;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * 「매월 25일」처럼 사람이 읽는 주기 문구.
 *
 * <p>서버가 만든다. 화면마다 조립하면 목록·상세·캘린더에서 같은 규칙이 다르게 읽히고,
 * 「매월 말일」과 「매월 31일」처럼 <b>같아 보이지만 다른</b> 규칙이 특히 그렇다.
 */
final class LedgerFrequencyLabel {

    private LedgerFrequencyLabel() {
    }

    static String of(LedgerRecurring rule) {
        Integer day = rule.getFreqDay();
        Integer interval = rule.getFreqInterval();
        return switch (rule.getFreqType()) {
            case WEEKLY -> "매주 " + dayOfWeek(day) + "요일";
            case MONTHLY_DAY -> "매월 " + (day == null ? "?" : day) + "일";
            case MONTHLY_LAST -> "매월 말일";
            case EVERY_N_MONTHS -> "%d개월마다 %s일".formatted(
                    interval == null ? 1 : interval, day == null ? "?" : day);
            case YEARLY -> "매년 %s월 %s일".formatted(
                    rule.getFreqMonth() == null ? "?" : rule.getFreqMonth(),
                    day == null ? "?" : day);
            case EVERY_N_DAYS -> "%d일마다".formatted(interval == null ? 1 : interval);
        };
    }

    private static String dayOfWeek(Integer day) {
        if (day == null || day < 1 || day > 7) {
            return "?";
        }
        return DayOfWeek.of(day).getDisplayName(TextStyle.NARROW, Locale.KOREAN);
    }
}
