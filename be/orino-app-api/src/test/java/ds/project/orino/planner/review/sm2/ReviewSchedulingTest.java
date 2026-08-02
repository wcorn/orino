package ds.project.orino.planner.review.sm2;

import ds.project.orino.common.time.StudyDay;
import ds.project.orino.domain.planner.review.entity.Rating;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    @DisplayName("같은 학습일 안에서는 몇 시에 복습하든 due 시각이 같다 (항상 04:00)")
    void rollover_is_independent_of_time_of_day() {
        // 6/1 05:00과 6/2 01:30은 둘 다 학습일 6/1이다(경계는 04:00)
        Instant earlyInDay = atSeoul(2026, 6, 1, 5, 0);
        Instant lateAtNight = atSeoul(2026, 6, 2, 1, 30);

        Instant fromEarly = ReviewSchedule.computeScheduledAt(Rating.EASY, 4, earlyInDay, SEOUL);
        Instant fromLate = ReviewSchedule.computeScheduledAt(Rating.EASY, 4, lateAtNight, SEOUL);

        assertThat(fromEarly).isEqualTo(atSeoul(2026, 6, 5, 4, 0));
        assertThat(fromLate).isEqualTo(atSeoul(2026, 6, 5, 4, 0));
    }

    @Test
    @DisplayName("롤오버 04:00은 사용자 시간대 기준이라 UTC로는 전날 19:00이다")
    void rollover_is_user_timezone_based() {
        Instant now = atSeoul(2026, 6, 1, 13, 0);

        Instant result = ReviewSchedule.computeScheduledAt(Rating.GOOD, 1, now, SEOUL);

        // 사용자 TZ 6/2 04:00 = UTC 6/1 19:00
        assertThat(result).isEqualTo(Instant.parse("2026-06-01T19:00:00Z"));
    }

    @Nested
    @DisplayName("학습일 경계 04:00 (#1003)")
    class StudyDayBoundary {

        @Test
        @DisplayName("새벽 1시 복습은 아직 전날 몫 — 간격 1일이면 그날 04:00(3시간 뒤)")
        void late_night_review_schedules_same_morning() {
            Instant oneAm = atSeoul(2026, 6, 2, 1, 0);

            Instant result = ReviewSchedule.computeScheduledAt(Rating.GOOD, 1, oneAm, SEOUL);

            // 자정 기준이었다면 6/3 04:00(27시간 뒤)이라 오후에 보면 36시간이 벌어졌다
            assertThat(result).isEqualTo(atSeoul(2026, 6, 2, 4, 0));
        }

        @Test
        @DisplayName("03:59와 04:01은 학습일이 갈린다")
        void boundary_splits_at_four() {
            Instant justBefore = ReviewSchedule.computeScheduledAt(
                    Rating.GOOD, 1, atSeoul(2026, 6, 2, 3, 59), SEOUL);
            Instant justAfter = ReviewSchedule.computeScheduledAt(
                    Rating.GOOD, 1, atSeoul(2026, 6, 2, 4, 1), SEOUL);

            assertThat(justBefore).isEqualTo(atSeoul(2026, 6, 2, 4, 0));
            assertThat(justAfter).isEqualTo(atSeoul(2026, 6, 3, 4, 0));
        }

        @Test
        @DisplayName("첫 복습도 학습일 기준 — 새벽 1시에 만든 카드는 그날 04:00")
        void first_review_follows_study_day() {
            Instant oneAm = atSeoul(2026, 6, 2, 1, 0);
            LocalDate studyDay = StudyDay.of(oneAm, SEOUL);

            ReviewSchedule first = ReviewSchedule.firstReview(1L, 1L, studyDay, SEOUL);

            assertThat(studyDay).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(first.getScheduledAt()).isEqualTo(atSeoul(2026, 6, 2, 4, 0));
        }

        @Test
        @DisplayName("새벽 1시에 본 복습은 예정일 당일로 쳐서 밀린 일수가 0이다")
        void elapsed_days_uses_study_day() {
            ReviewSchedule review = new ReviewSchedule(
                    1L, 1L, 2, atSeoul(2026, 6, 1, 4, 0), 1, new BigDecimal("2.50"));

            // 6/2 01:00은 학습일 6/1 — 예정일(6/1)과 같은 날이라 밀리지 않았다
            review.complete(Rating.GOOD, atSeoul(2026, 6, 2, 1, 0), SEOUL);

            assertThat(review.getElapsedDays()).isZero();
        }

        @Test
        @DisplayName("짝 카드 미루기도 학습일 기준 — 새벽 1시면 그날 04:00으로 밀린다")
        void bury_follows_study_day() {
            ReviewSchedule review = new ReviewSchedule(
                    1L, 1L, 2, atSeoul(2026, 6, 1, 4, 0), 1, new BigDecimal("2.50"));

            review.bury(atSeoul(2026, 6, 2, 1, 0), SEOUL);

            assertThat(review.getScheduledAt()).isEqualTo(atSeoul(2026, 6, 2, 4, 0));
        }

        @Test
        @DisplayName("낮 시간대는 예전과 동작이 같다")
        void daytime_is_unchanged() {
            Instant afternoon = atSeoul(2026, 6, 1, 15, 0);

            assertThat(ReviewSchedule.computeScheduledAt(Rating.GOOD, 6, afternoon, SEOUL))
                    .isEqualTo(atSeoul(2026, 6, 7, 4, 0));
        }
    }
}
