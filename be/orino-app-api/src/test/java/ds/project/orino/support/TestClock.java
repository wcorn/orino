package ds.project.orino.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 시각을 못박았다 풀 수 있는 Clock.
 *
 * <p>못박을 시각마다 {@code @TestConfiguration}을 따로 두면 스프링이 그 수만큼 컨텍스트를
 * 새로 띄운다 — 설정이 한 글자만 달라도 다른 컨텍스트이고, 각각이 EntityManagerFactory와
 * 커넥션 풀을 물고 캐시에 남는다. 그런데 "지금이 몇 시인가"는 설정이 아니라 테스트가 정하는
 * 값이다. 컨텍스트는 하나로 두고 값만 갈아끼운다.
 *
 * <p>풀린 상태에서는 {@link Clock#systemUTC()}와 똑같이 동작한다 — 프로덕션 빈이 그것이다.
 * 그래서 시각을 안 건드리는 테스트는 지금까지와 달라지는 것이 없다.
 *
 * <p>{@link #withZone(ZoneId)}가 돌려주는 뷰는 못박은 시각을 <b>같이</b> 본다. 프로덕션
 * 코드가 {@code LocalDate.now(clock.withZone(zone))} 꼴로 존을 갈아끼워 쓰는데, 뷰가 값을
 * 복사해 가면 만든 시점 이후의 변경을 놓친다.
 */
public class TestClock extends Clock {

    /** 비어 있으면 실시각. 존 뷰들이 이 참조를 공유한다. */
    private final AtomicReference<Instant> pinned;

    private final ZoneId zone;

    public TestClock() {
        this(ZoneOffset.UTC, new AtomicReference<>());
    }

    private TestClock(ZoneId zone, AtomicReference<Instant> pinned) {
        this.zone = zone;
        this.pinned = pinned;
    }

    /** 이 시각으로 못박는다. */
    public void fixAt(Instant instant) {
        pinned.set(instant);
    }

    /** 실시각으로 되돌린다. */
    public void release() {
        pinned.set(null);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return newZone.equals(zone) ? this : new TestClock(newZone, pinned);
    }

    @Override
    public Instant instant() {
        Instant fixed = pinned.get();
        return fixed != null ? fixed : Instant.now();
    }
}
