package ds.project.orino.planner.travel.day;

import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripStatus;
import ds.project.orino.planner.travel.day.service.TripClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여행의 "오늘"을 정하는 규칙을 고정한다. v2.1에서 <b>여행에는 타임존이 없으므로</b> 어느
 * 날짜의 기준 도시로 판정하느냐가 상태·D-day의 전부다.
 *
 * <p>모든 테스트가 <b>기기 타임존을 UTC로 고정</b>한 시계를 쓴다 — 판정이 기기와 무관해야
 * 한다는 것이 이 계산의 존재 이유다.
 */
class TripClockTest {

    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    private static final ZoneId HONOLULU = ZoneId.of("Pacific/Honolulu");

    private static final LocalDate OCT24 = LocalDate.of(2026, 10, 24);
    private static final LocalDate OCT27 = LocalDate.of(2026, 10, 27);

    /** 기기 시간대와 무관해야 하므로 시스템 존을 일부러 여행지와 다르게 준다. */
    private static Clock utcClockAt(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneId.of("UTC"));
    }

    private static Trip trip() {
        return new Trip(1L, "도쿄 3박4일", OCT24, OCT27);
    }

    /** 전 날짜가 같은 도시인 여행. 마이그레이션 직후의 모든 여행이 이 모양이다. */
    private static Map<LocalDate, TravelPlace> allIn(ZoneId zone) {
        Map<LocalDate, TravelPlace> cities = new LinkedHashMap<>();
        for (LocalDate date = OCT24; !date.isAfter(OCT27); date = date.plusDays(1)) {
            cities.put(date, city(zone));
        }
        return cities;
    }

    private static TravelPlace city(ZoneId zone) {
        return TravelPlace.manualCity(1L, zone.getId(), zone.getId(), "JPY");
    }

    @Nested
    @DisplayName("오늘 판정")
    class Today {

        @Test
        @DisplayName("기간 안이면 그 날짜의 기준 도시 시계로 오늘을 정한다")
        void withinPeriod() {
            // 10/25 03:00 UTC = 도쿄 10/25 12:00
            assertThat(TripClock.today(trip(), allIn(TOKYO), utcClockAt("2026-10-25T03:00:00Z")))
                    .isEqualTo(LocalDate.of(2026, 10, 25));
        }

        @Test
        @DisplayName("기기로는 아직 어제여도 그 도시가 자정을 넘겼으면 오늘이 넘어간다")
        void crossesMidnightBeforeDevice() {
            // 10/24 16:00 UTC — UTC로는 10/24지만 도쿄는 이미 10/25 01:00이다.
            Clock clock = utcClockAt("2026-10-24T16:00:00Z");

            assertThat(LocalDate.now(clock)).isEqualTo(OCT24);
            assertThat(TripClock.today(trip(), allIn(TOKYO), clock))
                    .isEqualTo(LocalDate.of(2026, 10, 25));
        }

        @Test
        @DisplayName("여행 안에 타임존이 둘이면 날짜마다 자기 시계로 지나갔는지 묻는다")
        void mixedTimezones() {
            // 10/25는 호놀룰루(UTC-10), 나머지는 도쿄. 10/25 05:00 UTC —
            // 도쿄 시계로 10/24는 이미 지났고(10/25 14:00), 호놀룰루 시계로 10/25는 아직이다(10/24 19:00).
            Map<LocalDate, TravelPlace> cities = allIn(TOKYO);
            cities.put(LocalDate.of(2026, 10, 25), city(HONOLULU));

            assertThat(TripClock.today(trip(), cities, utcClockAt("2026-10-25T05:00:00Z")))
                    .isEqualTo(LocalDate.of(2026, 10, 25));
        }

        @Test
        @DisplayName("\"그 날짜의 오늘이 그 날짜인가\"로 물으면 어느 날짜도 답하지 못하는 순간이 있다")
        void noDayClaimsTodayWithMixedZones() {
            // 위와 같은 순간이다. 도쿄 날짜들은 전부 "지금은 10/25"라 하고 10/25는 호놀룰루라
            // "지금은 10/24"라 한다 — 자기 날짜와 같다고 답하는 날짜가 하나도 없다.
            // 그래서 판정을 "같은가"가 아니라 "지나갔나"로 한다.
            Map<LocalDate, TravelPlace> cities = allIn(TOKYO);
            cities.put(LocalDate.of(2026, 10, 25), city(HONOLULU));
            Clock clock = utcClockAt("2026-10-25T05:00:00Z");

            assertThat(cities.entrySet()).noneSatisfy(entry ->
                    assertThat(LocalDate.now(clock.withZone(
                            ZoneId.of(entry.getValue().getTimezone()))))
                            .isEqualTo(entry.getKey()));
            assertThat(TripClock.today(trip(), cities, clock)).isNotNull();
        }
    }

    @Nested
    @DisplayName("상태")
    class Status {

        @Test
        @DisplayName("시작일·종료일 당일도 진행 중이다")
        void ongoingIncludesBothEnds() {
            assertThat(TripClock.status(trip(), allIn(TOKYO), utcClockAt("2026-10-24T03:00:00Z")))
                    .isEqualTo(TripStatus.ONGOING);
            assertThat(TripClock.status(trip(), allIn(TOKYO), utcClockAt("2026-10-27T03:00:00Z")))
                    .isEqualTo(TripStatus.ONGOING);
        }

        @Test
        @DisplayName("기간 밖(앞)이면 첫날 도시 시계로 판정한다")
        void beforeStartUsesFirstDay() {
            assertThat(TripClock.status(trip(), allIn(TOKYO), utcClockAt("2026-10-20T03:00:00Z")))
                    .isEqualTo(TripStatus.UPCOMING);
        }

        @Test
        @DisplayName("기간 밖(뒤)이면 마지막 날 도시 시계로 판정한다")
        void afterEndUsesLastDay() {
            assertThat(TripClock.status(trip(), allIn(TOKYO), utcClockAt("2026-10-28T03:00:00Z")))
                    .isEqualTo(TripStatus.COMPLETED);
        }

        @Test
        @DisplayName("마지막 날 도시가 늦은 시간대면 그 도시에서 끝날 때까지 진행 중이다")
        void lastDayZoneDecidesCompletion() {
            // 마지막 날만 호놀룰루(UTC-10). 10/28 03:00 UTC면 도쿄는 10/28이지만
            // 호놀룰루는 아직 10/27 17:00이라 여행이 안 끝났다.
            Map<LocalDate, TravelPlace> cities = allIn(TOKYO);
            cities.put(OCT27, city(HONOLULU));

            assertThat(TripClock.status(trip(), cities, utcClockAt("2026-10-28T03:00:00Z")))
                    .isEqualTo(TripStatus.ONGOING);
        }

        @Test
        @DisplayName("날짜 행이 없으면 기기 타임존으로 버틴다 — 목록 전체를 죽이지 않는다")
        void survivesMissingDays() {
            assertThat(TripClock.status(trip(), Map.of(), utcClockAt("2026-10-20T03:00:00Z")))
                    .isEqualTo(TripStatus.UPCOMING);
        }
    }

    @Nested
    @DisplayName("D-day")
    class DDay {

        @Test
        @DisplayName("첫날 도시의 오늘에서 시작일까지 센다")
        void countsFromFirstDayZone() {
            assertThat(TripClock.daysUntilStart(trip(), allIn(TOKYO),
                    utcClockAt("2026-10-20T03:00:00Z"))).isEqualTo(4);
            assertThat(TripClock.daysUntilStart(trip(), allIn(TOKYO),
                    utcClockAt("2026-10-24T03:00:00Z"))).isZero();
        }

        @Test
        @DisplayName("첫날 도시가 자정을 넘기면 D-day가 하루 줄어든다 — 기기는 아직 어제다")
        void followsFirstDayMidnight() {
            // UTC로는 10/23이지만 도쿄는 이미 10/24 01:00 → 출발 당일이다.
            Clock clock = utcClockAt("2026-10-23T16:00:00Z");

            assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 10, 23));
            assertThat(TripClock.daysUntilStart(trip(), allIn(TOKYO), clock)).isZero();
        }

        @Test
        @DisplayName("첫날 도시가 날짜변경선 반대편이면 아직 하루 남았다")
        void firstDayBehindUtc() {
            Map<LocalDate, TravelPlace> cities = allIn(TOKYO);
            cities.put(OCT24, city(HONOLULU));
            // 10/24 05:00 UTC — 호놀룰루는 아직 10/23 19:00이라 출발 전이다.
            assertThat(TripClock.daysUntilStart(trip(), cities, utcClockAt("2026-10-24T05:00:00Z")))
                    .isEqualTo(1);
        }
    }
}
