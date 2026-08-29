package ds.project.orino.support;

/**
 * 테스트가 못박는 시각들. {@link FixedClock}의 값으로 쓸 수 있도록 문자열 상수다.
 */
public final class TestInstants {

    /**
     * KST 2026-01-15 11:00. 학습일 롤오버(04:00)를 아예 지나지 않는 평범한 낮이라,
     * "앱의 오늘"과 "달력 오늘"이 같다.
     */
    public static final String FIXED_NOW = "2026-01-15T02:00:00Z";

    /**
     * KST 2026-01-16 01:00 — 롤오버 3시간 전.
     *
     * <p>{@link #FIXED_NOW}는 경계를 지나지 않아서, "앱의 오늘"과 "달력 오늘"이 어긋나는
     * 하루 네 시간(자정~04:00)이 어떤 테스트에도 안 걸렸다. 그 창에서만 깨지는 결함이
     * 6일간 잠복한 적이 있다(#1055).
     */
    public static final String PRE_ROLLOVER_NOW = "2026-01-15T16:00:00Z";

    /** {@link #PRE_ROLLOVER_NOW}의 학습일. 달력 날짜와 하루 다르다. */
    public static final String PRE_ROLLOVER_STUDY_DAY = "2026-01-15";

    /** {@link #PRE_ROLLOVER_NOW}의 달력 날짜. 둘이 다르다는 것이 이 시각의 존재 이유다. */
    public static final String PRE_ROLLOVER_CALENDAR_DAY = "2026-01-16";

    private TestInstants() {
    }
}
