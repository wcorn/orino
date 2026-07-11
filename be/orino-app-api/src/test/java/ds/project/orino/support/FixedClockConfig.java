package ds.project.orino.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * 복습 "now"를 고정 시각으로 못박는 테스트 설정. whenKind(now/today/future)·doneToday 같은
 * 시각 의존 로직을 결정적으로 검증하기 위함이다. JWT는 실시각({@code new Date()})을 쓰므로
 * 이 Clock을 고정해도 인증에는 영향이 없다.
 *
 * <p>고정 시각 {@code 2026-01-15T02:00:00Z} = KST 2026-01-15 11:00. 즉 사용자(Asia/Seoul) 기준
 * 오늘은 2026-01-15, 현재 11:00.
 */
@TestConfiguration
public class FixedClockConfig {

    /** 고정 현재 시각(UTC). KST로는 2026-01-15 11:00. */
    public static final Instant FIXED_NOW = Instant.parse("2026-01-15T02:00:00Z");

    @Bean
    @Primary
    public Clock fixedClock() {
        return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    }
}
