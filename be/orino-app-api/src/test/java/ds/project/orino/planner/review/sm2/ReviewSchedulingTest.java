package ds.project.orino.planner.review.sm2;

import ds.project.orino.domain.planner.review.entity.Rating;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewSchedule.computeScheduledAt - datetime 하이브리드 스케줄링 (UTC 저장 + 사용자 TZ 기준 계산)")
class ReviewSchedulingTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 사용자 TZ 로컬 시각을 UTC Instant로 변환하는 헬퍼. */
    private static Instant atSeoul(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(SEOUL).toInstant();
    }

    @Test
    @DisplayName("AGAIN은 당일 10분 뒤(분 단위)로 잡힌다")
    void again_reschedules_in_ten_minutes() {
        Instant now = atSeoul(2026, 6, 1, 13, 0);

        Instant result = ReviewSchedule.computeScheduledAt(Rating.AGAIN, 1, now, SEOUL);

        assertThat(result).isEqualTo(now.plusSeconds(600));
    }

    @Test
    @DisplayName("GOOD 등 다중일 복습은 사용자 TZ 기준 (오늘+간격) 날짜의 04:00(롤오버)로 잡힌다")
    void good_reschedules_at_rollover_hour() {
        Instant now = atSeoul(2026, 6, 1, 13, 0);

        Instant result = ReviewSchedule.computeScheduledAt(Rating.GOOD, 6, now, SEOUL);

        assertThat(result).isEqualTo(atSeoul(2026, 6, 7, 4, 0));
    }

    @Test
    @DisplayName("다중일 due 시각은 복습한 시각과 무관하게 항상 사용자 TZ 04:00이다")
    void rollover_is_independent_of_time_of_day() {
        Instant morning = atSeoul(2026, 6, 1, 1, 30);
        Instant evening = atSeoul(2026, 6, 1, 23, 45);

        Instant fromMorning = ReviewSchedule.computeScheduledAt(Rating.EASY, 4, morning, SEOUL);
        Instant fromEvening = ReviewSchedule.computeScheduledAt(Rating.EASY, 4, evening, SEOUL);

        assertThat(fromMorning).isEqualTo(atSeoul(2026, 6, 5, 4, 0));
        assertThat(fromEvening).isEqualTo(atSeoul(2026, 6, 5, 4, 0));
    }

    @Test
    @DisplayName("롤오버 04:00은 사용자 시간대 기준이라 UTC로는 전날 19:00이다")
    void rollover_is_user_timezone_based() {
        Instant now = atSeoul(2026, 6, 1, 13, 0);

        Instant result = ReviewSchedule.computeScheduledAt(Rating.GOOD, 1, now, SEOUL);

        // 사용자 TZ 6/2 04:00 = UTC 6/1 19:00
        assertThat(result).isEqualTo(Instant.parse("2026-06-01T19:00:00Z"));
    }
}
