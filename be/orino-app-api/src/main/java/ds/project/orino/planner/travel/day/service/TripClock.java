package ds.project.orino.planner.travel.day.service;

import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * 여행의 "오늘"을 정한다. <b>여행에는 타임존이 없으므로</b>(v2.1) 어느 날짜의 기준 도시로
 * 판정할지부터 정해야 한다.
 *
 * <p><b>날짜마다 자기 시계로 "나는 지나갔나"를 묻는다.</b> 아직 지나지 않은 첫 날짜가 오늘이다.
 *
 * <pre>
 * 10.24 오사카(UTC+9)   오사카 시계로 이미 10.25? → 지나갔다
 * 10.25 호놀룰루(UTC-10) 호놀룰루 시계로 아직 10.24 → 안 지나갔다 → 오늘은 10.25
 * </pre>
 *
 * <p>"그 날짜의 시계로 본 오늘이 그 날짜와 같은가"로 물으면 안 된다. 날짜변경선을 넘나드는
 * 여행에서는 <b>어느 날짜도 그 조건을 만족하지 않는 순간</b>이 생긴다 — 오사카는 벌써 25일인데
 * 25일에 배정된 호놀룰루는 아직 24일인 식이다. "지나갔나"로 물으면 그런 구멍이 없다.
 *
 * <p>상태는 양 끝만 본다 — <b>첫날 도시가 시작일에 닿기 전이면 예정, 마지막 날 도시가 종료일을
 * 넘겼으면 완료.</b> 그 사이는 전부 진행 중이다. 출발 전에는 떠나는 곳의 시계를, 다녀온 뒤에는
 * 마지막에 있던 곳의 시계를 보는 셈이라 사용자가 서 있는 곳에 가장 가깝다.
 */
public final class TripClock {

    private TripClock() {
    }

    /**
     * 화면이 열어야 할 "오늘" — <b>아직 지나지 않은 첫 날짜</b>다. 전부 지나갔으면 마지막 날.
     *
     * @param cities 날짜 → 기준 도시. <b>기간의 모든 날짜</b>가 들어 있어야 한다
     */
    public static LocalDate today(Trip trip, Map<LocalDate, TravelPlace> cities, Clock clock) {
        for (LocalDate date = trip.getStartDate(); !date.isAfter(trip.getEndDate());
                date = date.plusDays(1)) {
            if (!hasPassed(date, cities, clock)) {
                return date;
            }
        }
        return trip.getEndDate();
    }

    /** 오늘에 해당하는 날짜의 기준 도시 타임존으로 판정한 상태. */
    public static TripStatus status(Trip trip, Map<LocalDate, TravelPlace> cities, Clock clock) {
        if (nowOn(trip.getStartDate(), cities, clock).isBefore(trip.getStartDate())) {
            return TripStatus.UPCOMING;
        }
        if (hasPassed(trip.getEndDate(), cities, clock)) {
            return TripStatus.COMPLETED;
        }
        return TripStatus.ONGOING;
    }

    /**
     * 시작일까지 남은 일수. 기준은 <b>첫날</b>의 기준 도시다 — "언제 출발하나"는 출발하는
     * 곳의 시계로 세는 값이다.
     */
    public static long daysUntilStart(Trip trip, Map<LocalDate, TravelPlace> cities, Clock clock) {
        return ChronoUnit.DAYS.between(
                nowOn(trip.getStartDate(), cities, clock), trip.getStartDate());
    }

    /**
     * 그 날짜의 타임존. 날짜 행이 없으면(있을 수 없는 상태) 기기 타임존으로 버틴다 —
     * 한 건의 데이터 문제로 목록 전체가 사라지는 편이 더 나쁘다.
     */
    public static ZoneId zoneOn(LocalDate date, Map<LocalDate, TravelPlace> cities) {
        TravelPlace city = cities.get(date);
        return city == null ? ZoneId.systemDefault() : TripDayService.zoneOf(city);
    }

    /** 그 날짜의 도시에서 본 오늘. */
    private static LocalDate nowOn(LocalDate date, Map<LocalDate, TravelPlace> cities,
                                   Clock clock) {
        return LocalDate.now(clock.withZone(zoneOn(date, cities)));
    }

    /** 그 날짜가 자기 도시의 시계로 이미 지나갔는가. */
    private static boolean hasPassed(LocalDate date, Map<LocalDate, TravelPlace> cities,
                                     Clock clock) {
        return nowOn(date, cities, clock).isAfter(date);
    }
}
