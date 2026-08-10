package ds.project.orino.domain.planner.travel.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 저장하지 않고 파생하는 값들(상태·D-day·일차)을 고정한다. 이 계산이 틀리면 컬럼이 없으니
 * DB를 봐도 알 수 없다 — 여기가 유일한 안전망이다.
 *
 * <p>v2.1부터 판정 타임존은 여행이 아니라 <b>날짜의 기준 도시</b>에서 온다. 여행은 타임존을
 * 갖지 않으므로 여기서는 호출부가 하듯 존을 인자로 넣어 준다.
 */
class TripTest {

    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    private static final ZoneId HONOLULU = ZoneId.of("Pacific/Honolulu");

    /** 도쿄 여행 10/24~10/27. 에픽의 실사용 일정 그대로다. */
    private static Trip tokyoTrip() {
        return new Trip(1L, "도쿄", LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 27));
    }

    /** 기기 시간대와 무관하게 판정돼야 하므로, 시스템 존을 일부러 여행지와 다르게 준다. */
    private static Clock utcClockAt(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneId.of("UTC"));
    }

    @Nested
    @DisplayName("상태 판정")
    class Status {

        @Test
        @DisplayName("오늘이 시작일 전이면 예정")
        void upcomingBeforeStart() {
            assertThat(tokyoTrip().status(utcClockAt("2026-10-20T03:00:00Z"), TOKYO))
                    .isEqualTo(TripStatus.UPCOMING);
        }

        @Test
        @DisplayName("시작일·종료일 당일도 진행 중이다(경계 포함)")
        void ongoingIncludesBothEnds() {
            assertThat(tokyoTrip().status(utcClockAt("2026-10-24T03:00:00Z"), TOKYO))
                    .isEqualTo(TripStatus.ONGOING);
            assertThat(tokyoTrip().status(utcClockAt("2026-10-27T03:00:00Z"), TOKYO))
                    .isEqualTo(TripStatus.ONGOING);
        }

        @Test
        @DisplayName("종료일 다음날부터 완료")
        void completedAfterEnd() {
            assertThat(tokyoTrip().status(utcClockAt("2026-10-28T03:00:00Z"), TOKYO))
                    .isEqualTo(TripStatus.COMPLETED);
        }

        @Test
        @DisplayName("기기 시간대가 아니라 기준 도시 타임존의 오늘로 판정한다")
        void derivedFromBaseCityTimezoneNotDeviceZone() {
            // 2026-10-23 16:00 UTC — 서울/UTC로는 아직 10/23이지만 도쿄는 이미 10/24 01:00이다.
            Clock justBeforeMidnightUtc = utcClockAt("2026-10-23T16:00:00Z");

            assertThat(LocalDate.now(justBeforeMidnightUtc)).isEqualTo(LocalDate.of(2026, 10, 23));
            assertThat(tokyoTrip().todayIn(justBeforeMidnightUtc, TOKYO))
                    .isEqualTo(LocalDate.of(2026, 10, 24));
            // 도쿄 기준으로는 여행이 이미 시작됐다. UTC로 판정했다면 예정으로 잘못 나온다.
            assertThat(tokyoTrip().status(justBeforeMidnightUtc, TOKYO))
                    .isEqualTo(TripStatus.ONGOING);
        }

        @Test
        @DisplayName("날짜변경선 반대편(하와이) 여행도 그 지역 오늘로 판정한다")
        void worksForZonesBehindUtc() {
            Trip honolulu = new Trip(1L, "하와이", LocalDate.of(2026, 10, 24),
                    LocalDate.of(2026, 10, 27));
            // 10/24 05:00 UTC — 호놀룰루(UTC-10)는 아직 10/23 19:00이라 출발 전이다.
            Clock clock = utcClockAt("2026-10-24T05:00:00Z");

            assertThat(honolulu.todayIn(clock, HONOLULU)).isEqualTo(LocalDate.of(2026, 10, 23));
            assertThat(honolulu.status(clock, HONOLULU)).isEqualTo(TripStatus.UPCOMING);
        }

        @Test
        @DisplayName("statusOn은 주어진 날짜를 오늘로 본다(목록에서 오늘을 재사용)")
        void statusOnUsesGivenDate() {
            Trip trip = tokyoTrip();
            assertThat(trip.statusOn(LocalDate.of(2026, 10, 23))).isEqualTo(TripStatus.UPCOMING);
            assertThat(trip.statusOn(LocalDate.of(2026, 10, 25))).isEqualTo(TripStatus.ONGOING);
            assertThat(trip.statusOn(LocalDate.of(2026, 10, 28))).isEqualTo(TripStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("D-day · 일차")
    class Derived {

        @Test
        @DisplayName("D-day는 기준 도시 타임존의 오늘에서 시작일까지 남은 일수")
        void daysUntilStart() {
            assertThat(tokyoTrip().daysUntilStart(utcClockAt("2026-10-20T03:00:00Z"), TOKYO))
                    .isEqualTo(4);
        }

        @Test
        @DisplayName("시작 당일은 0, 시작 후에는 음수")
        void daysUntilStartOnAndAfterStart() {
            assertThat(tokyoTrip().daysUntilStart(utcClockAt("2026-10-24T03:00:00Z"), TOKYO))
                    .isZero();
            assertThat(tokyoTrip().daysUntilStart(utcClockAt("2026-10-26T03:00:00Z"), TOKYO))
                    .isEqualTo(-2);
        }

        @Test
        @DisplayName("총 일수는 당일을 포함한다(3박4일 = 4)")
        void totalDaysIsInclusive() {
            assertThat(tokyoTrip().totalDays()).isEqualTo(4);

            Trip oneDay = new Trip(1L, "당일치기", LocalDate.of(2026, 10, 24),
                    LocalDate.of(2026, 10, 24));
            assertThat(oneDay.totalDays()).isEqualTo(1);
        }

        @Test
        @DisplayName("일차 번호는 시작일이 1일차")
        void dayNumberStartsAtOne() {
            Trip trip = tokyoTrip();
            assertThat(trip.dayNumberOf(LocalDate.of(2026, 10, 24))).isEqualTo(1);
            assertThat(trip.dayNumberOf(LocalDate.of(2026, 10, 27))).isEqualTo(4);
        }

        @Test
        @DisplayName("covers는 기간 경계를 포함하고 밖은 배제한다")
        void coversIsInclusive() {
            Trip trip = tokyoTrip();
            assertThat(trip.covers(LocalDate.of(2026, 10, 23))).isFalse();
            assertThat(trip.covers(LocalDate.of(2026, 10, 24))).isTrue();
            assertThat(trip.covers(LocalDate.of(2026, 10, 27))).isTrue();
            assertThat(trip.covers(LocalDate.of(2026, 10, 28))).isFalse();
        }
    }

    @Test
    @DisplayName("같은 순간이라도 기준 도시가 다르면 상태·D-day가 다르게 나온다")
    void derivationFollowsBaseCityZone() {
        Trip trip = tokyoTrip();
        Clock clock = utcClockAt("2026-10-23T16:00:00Z");

        // 도쿄는 이미 10/24 — 여행이 시작됐다.
        assertThat(trip.status(clock, TOKYO)).isEqualTo(TripStatus.ONGOING);
        // 같은 기간을 호놀룰루 기준으로 보면 아직 10/23이라 출발 전이다.
        assertThat(trip.status(clock, HONOLULU)).isEqualTo(TripStatus.UPCOMING);
        assertThat(trip.daysUntilStart(clock, HONOLULU)).isEqualTo(1);
    }
}
