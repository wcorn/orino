package ds.project.orino.planner.dayplan;

/** 주간 계획표 시간 변환: API "HH:mm" 문자열 ↔ 자정 기준 분(0~1440, 1440="24:00"). */
public final class WeeklyPlanTime {

    public static final int DAY_MINUTES = 24 * 60; // 1440

    private WeeklyPlanTime() {
    }

    /** "HH:mm" → 분(0~1440). 형식이 잘못되면 {@link IllegalArgumentException}. */
    public static int toMinutes(String hhmm) {
        if (hhmm == null) {
            throw new IllegalArgumentException("시간이 비어있습니다");
        }
        String[] parts = hhmm.trim().split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("HH:mm 형식이 아닙니다: " + hhmm);
        }
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        if (hour < 0 || minute < 0 || minute > 59) {
            throw new IllegalArgumentException("유효하지 않은 시각: " + hhmm);
        }
        return hour * 60 + minute;
    }

    /** 분(0~1440) → "HH:mm"(1440="24:00"). */
    public static String toHhmm(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }
}
