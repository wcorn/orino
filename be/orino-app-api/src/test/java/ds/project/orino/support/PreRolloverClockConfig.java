package ds.project.orino.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * 학습일 롤오버(04:00) <b>이전</b>으로 못박은 Clock.
 *
 * <p>{@link FixedClockConfig}는 KST 11:00이라 경계를 아예 지나지 않는다. 그래서 "앱의 오늘"과
 * "달력 오늘"이 어긋나는 하루 4시간(자정~04:00)이 어떤 테스트에도 안 걸렸고, 실제로 그 창에서만
 * 깨지는 결함이 6일간 잠복했다(#1055).
 *
 * <p>고정 시각 {@code 2026-01-15T16:00:00Z} = KST <b>2026-01-16 01:00</b>.
 * 달력으로는 1/16이지만 학습일은 아직 <b>1/15</b>다.
 */
@TestConfiguration
public class PreRolloverClockConfig {

    /** 고정 현재 시각(UTC). KST로는 2026-01-16 01:00 — 롤오버 3시간 전. */
    public static final Instant FIXED_NOW = Instant.parse("2026-01-15T16:00:00Z");

    /** 그 시각의 학습일. 달력 날짜(1/16)와 하루 다르다. */
    public static final String STUDY_DAY = "2026-01-15";

    /** 달력 기준으로는 이 날짜다. 둘이 다르다는 것이 이 설정의 존재 이유다. */
    public static final String CALENDAR_DAY = "2026-01-16";

    @Bean
    @Primary
    public Clock preRolloverClock() {
        return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    }
}
