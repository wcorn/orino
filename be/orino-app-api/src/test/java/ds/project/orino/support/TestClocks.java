package ds.project.orino.support;

import java.time.Instant;

/**
 * 테스트가 못박는 시각들.
 *
 * <p>예전에는 시각마다 {@code @TestConfiguration}이 하나씩 있었다. 이제 값만 남는다 —
 * 시각은 컨텍스트를 가를 일이 아니다({@link MutableTestClock}).
 */
public final class TestClocks {

    private TestClocks() {
    }

    /**
     * 기본 고정 시각. {@code 2026-01-15T02:00:00Z} = KST 2026-01-15 11:00.
     *
     * <p>JWT는 실시각({@code new Date()})을 쓰므로 이 시각을 못박아도 인증에는 영향이 없다.
     */
    public static final Instant FIXED = Instant.parse("2026-01-15T02:00:00Z");

    /**
     * 학습일 롤오버(04:00) <b>이전</b>. {@code 2026-01-15T16:00:00Z} = KST 2026-01-16 01:00.
     *
     * <p>{@link #FIXED}는 KST 11:00이라 경계를 아예 지나지 않는다. 그래서 「앱의 오늘」과
     * 「달력 오늘」이 어긋나는 하루 4시간(자정~04:00)이 어떤 테스트에도 안 걸렸고, 실제로 그
     * 창에서만 깨지는 결함이 6일간 잠복했다(#1055).
     */
    public static final Instant PRE_ROLLOVER = Instant.parse("2026-01-15T16:00:00Z");

    /** {@link #PRE_ROLLOVER} 시점의 학습일. 달력 날짜와 하루 다르다. */
    public static final String PRE_ROLLOVER_STUDY_DAY = "2026-01-15";

    /** 달력 기준으로는 이 날짜다. 둘이 다르다는 것이 이 시각의 존재 이유다. */
    public static final String PRE_ROLLOVER_CALENDAR_DAY = "2026-01-16";
}
