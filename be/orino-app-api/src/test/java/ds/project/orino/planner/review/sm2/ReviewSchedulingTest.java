package ds.project.orino.planner.review.sm2;

import ds.project.orino.domain.planner.review.entity.Rating;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewSchedule.computeScheduledAt - datetime 하이브리드 스케줄링")
class ReviewSchedulingTest {

    @Test
    @DisplayName("AGAIN은 당일 10분 뒤(분 단위)로 잡힌다")
    void again_reschedules_in_ten_minutes() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 13, 0);

        LocalDateTime result = ReviewSchedule.computeScheduledAt(Rating.AGAIN, 1, now);

        assertThat(result).isEqualTo(now.plusMinutes(10));
    }

    @Test
    @DisplayName("GOOD 등 다중일 복습은 (오늘+간격) 날짜의 04:00(롤오버)로 잡힌다")
    void good_reschedules_at_rollover_hour() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 13, 0);

        LocalDateTime result = ReviewSchedule.computeScheduledAt(Rating.GOOD, 6, now);

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 6, 7, 4, 0));
    }

    @Test
    @DisplayName("다중일 due 시각은 복습한 시각과 무관하게 항상 04:00이다")
    void rollover_is_independent_of_time_of_day() {
        LocalDateTime morning = LocalDateTime.of(2026, 6, 1, 1, 30);
        LocalDateTime evening = LocalDateTime.of(2026, 6, 1, 23, 45);

        LocalDateTime fromMorning = ReviewSchedule.computeScheduledAt(Rating.EASY, 4, morning);
        LocalDateTime fromEvening = ReviewSchedule.computeScheduledAt(Rating.EASY, 4, evening);

        assertThat(fromMorning).isEqualTo(LocalDateTime.of(2026, 6, 5, 4, 0));
        assertThat(fromEvening).isEqualTo(LocalDateTime.of(2026, 6, 5, 4, 0));
    }
}
