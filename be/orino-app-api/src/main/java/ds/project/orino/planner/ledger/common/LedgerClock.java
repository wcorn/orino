package ds.project.orino.planner.ledger.common;

import ds.project.orino.core.time.UserTimeZone;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 가계부의 「오늘」.
 *
 * <p>복습의 {@code StudyDay}(새벽 4시 롤오버)를 쓰지 않는다 — 그건 공부 흐름의 개념이다.
 * 가계부의 하루는 <b>달력 그대로</b>다. 새벽 2시에 산 편의점 커피는 그날 지출이다.
 *
 * <p>기준선을 서버가 정하는 이유는 화면마다 「오늘」을 다시 계산하면 시간대가 갈리는 순간
 * 예정과 확정의 경계가 두 곳에서 다르게 그어지기 때문이다.
 */
@Component
public class LedgerClock {

    private final Clock clock;

    public LedgerClock(Clock clock) {
        this.clock = clock;
    }

    /** 요청 시간대({@code X-Timezone}) 기준 오늘. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(UserTimeZone.get()));
    }

    public java.time.Instant now() {
        return clock.instant();
    }
}
