package ds.project.orino.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 시각을 갈아끼울 수 있는 {@link Clock}. <b>스프링 컨텍스트를 가르지 않고</b> 시간을 못박는다.
 *
 * <p>예전에는 못박을 시각마다 {@code @TestConfiguration}을 하나씩 두고 {@code @Import} 했다.
 * 스프링은 설정이 조금이라도 다르면 컨텍스트를 새로 만들어 캐시에 쌓으므로, 시각 하나 다른 것이
 * <b>EntityManagerFactory와 커넥션 풀을 통째로 한 벌 더</b> 띄우는 값을 치렀다.
 *
 * <p>시각은 테스트 <b>실행 시점</b>에 정한다 — 빈은 하나고 컨텍스트도 하나다.
 *
 * <p>{@link #withZone}은 스냅샷이 아니라 <b>같은 시각을 계속 따라보는</b> 시계를 준다.
 * 프로덕션 코드가 {@code clock.withZone(...)}으로 사용자 시간대를 입히므로, 여기서 값을
 * 복사해 버리면 나중에 못박은 시각이 반영되지 않는다.
 */
public class MutableTestClock extends Clock {

    /** {@code null}이면 실시각으로 돈다 — 시간을 신경 쓰지 않는 테스트의 기본값이다. */
    private volatile Instant fixed;

    private final ZoneId zone;

    public MutableTestClock() {
        this(ZoneOffset.UTC);
    }

    private MutableTestClock(ZoneId zone) {
        this.zone = zone;
    }

    /** 이 시각으로 못박는다. */
    public void set(Instant instant) {
        this.fixed = instant;
    }

    /** 실시각으로 되돌린다. 테스트마다 초기화해 앞 테스트의 시각이 새지 않게 한다. */
    public void reset() {
        this.fixed = null;
    }

    @Override
    public Instant instant() {
        Instant now = fixed;
        return now == null ? Instant.now() : now;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        // 시각은 공유하고 시간대만 바꾼다. 값을 복사하면 나중에 못박은 시각을 놓친다.
        return new Delegating(this, newZone);
    }

    /** 같은 원본을 보되 시간대만 다른 시계. */
    private static final class Delegating extends Clock {

        private final MutableTestClock source;
        private final ZoneId zone;

        private Delegating(MutableTestClock source, ZoneId zone) {
            this.source = source;
            this.zone = zone;
        }

        @Override
        public Instant instant() {
            return source.instant();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return new Delegating(source, newZone);
        }
    }
}
