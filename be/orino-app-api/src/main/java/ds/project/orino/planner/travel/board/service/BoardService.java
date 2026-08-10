package ds.project.orino.planner.travel.board.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.planner.travel.entity.TripStatus;
import ds.project.orino.domain.planner.travel.repository.ActivityDateCount;
import ds.project.orino.domain.planner.travel.entity.TripDay;
import ds.project.orino.domain.planner.travel.repository.TripActivityRepository;
import ds.project.orino.domain.planner.travel.repository.TripDayRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.activity.service.ActivityService;
import ds.project.orino.planner.travel.activity.dto.ActivityResponse;
import ds.project.orino.planner.travel.board.dto.BoardResponse;
import ds.project.orino.planner.travel.day.dto.BaseCityResponse;
import ds.project.orino.planner.travel.day.service.LegDeriver;
import ds.project.orino.planner.travel.day.service.TripClock;
import ds.project.orino.planner.travel.day.service.TripDayService;
import ds.project.orino.planner.travel.route.service.TravelTimeService;
import ds.project.orino.planner.travel.stay.service.StayBoardAssembler;
import ds.project.orino.planner.travel.tools.dto.WeatherResponse;
import ds.project.orino.planner.travel.tools.service.WeatherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 보드(S-04)의 단일 조회.
 *
 * <p>날짜 탭·보관함 건수·선택된 날짜의 일정을 한 응답에 담는다. 화면의 N+1 호출을 막는 것도
 * 있지만, 진짜 이유는 <b>오프라인 캐시가 응답 하나로 성립해야</b> 한다는 것이다 —
 * 응답을 쪼개면 비행기 모드에서 탭 하나만 살아난다.
 */
@Service
@Transactional(readOnly = true)
public class BoardService {

    private final TripRepository tripRepository;
    private final TripActivityRepository activityRepository;
    private final TripDayRepository dayRepository;
    private final ActivityService activityService;
    private final TripDayService tripDayService;
    private final TravelTimeService travelTimeService;
    private final WeatherService weatherService;
    private final StayBoardAssembler stayAssembler;
    private final Clock clock;

    public BoardService(TripRepository tripRepository,
                        TripActivityRepository activityRepository,
                        TripDayRepository dayRepository,
                        ActivityService activityService,
                        TripDayService tripDayService,
                        TravelTimeService travelTimeService,
                        WeatherService weatherService,
                        StayBoardAssembler stayAssembler,
                        Clock clock) {
        this.tripRepository = tripRepository;
        this.activityRepository = activityRepository;
        this.dayRepository = dayRepository;
        this.activityService = activityService;
        this.tripDayService = tripDayService;
        this.travelTimeService = travelTimeService;
        this.weatherService = weatherService;
        this.stayAssembler = stayAssembler;
        this.clock = clock;
    }

    /**
     * @param date    볼 날짜. {@code null}이면 서버가 고른다(아래 {@code defaultDate})
     * @param archive {@code true}면 날짜 대신 미배정 보관함을 본다
     */
    public BoardResponse board(Long memberId, Long tripId, LocalDate date, boolean archive) {
        Trip trip = tripRepository.findByIdAndMemberId(tripId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_TRIP_NOT_FOUND));
        Map<LocalDate, TravelPlace> cities = tripDayService.baseCitiesOf(tripId);
        TripStatus status = TripClock.status(trip, cities, clock);

        LocalDate selectedDate = archive ? null
                : resolveSelectedDate(trip, status, date, cities);
        List<TripActivity> activities = selectedDate == null
                ? activityRepository.findUnscheduled(tripId)
                : activityRepository.findAllByTripIdAndActivityDateOrderBySortOrderAscIdAsc(
                        tripId, selectedDate);

        // 선택한 날짜의 기준 도시. 도시 이탈 판정이 이 도시와 견준다.
        TravelPlace selectedCity = selectedDate == null ? null : cities.get(selectedDate);
        // 숙소는 여행 전체를 한 번에 읽는다 — 날짜 탭마다 조회하면 기간만큼 쿼리가 는다.
        StayBoardAssembler.Stays stays = stayAssembler.of(tripId);

        return new BoardResponse(
                buildTrip(trip, cities, status),
                buildDays(trip, cities, stays),
                selectedDate,
                activityRepository.countUnscheduled(tripId),
                withBaseCity(activityService.toResponses(activities), selectedCity),
                // 보관함은 날짜에 배정되지 않은 목록이라 순서에 이동 의미가 없다.
                // 계산해 봐야 화면에 쓰지 않고, 호출당 과금이라 그냥 낭비다.
                selectedDate == null ? List.of() : travelTimeService.travelTimes(activities),
                // 보관함에는 "그날 밤"이 없다 — 숙소 이동도 없다.
                selectedDate == null ? null
                        : stayAssembler.moveToStay(stays, selectedDate, activities, selectedCity));
    }

    /**
     * 여행 헤더. <b>타임존·통화가 없다</b>(v2.1) — 화면이 쓰던 자리는 {@code days[i].baseCity}다.
     *
     * <p>{@code singleCity}는 전 기간이 한 도시라는 뜻이고, 그때 날짜 탭은 도시명 대신
     * {@code N일차}로 그린다. 도시가 하나뿐인 여행에 도시명을 반복해 붙이면 정보가 아니라
     * 소음이다.
     */
    private static BoardResponse.BoardTrip buildTrip(Trip trip,
                                                     Map<LocalDate, TravelPlace> cities,
                                                     TripStatus status) {
        long cityCount = cities.values().stream().map(TravelPlace::getId).distinct().count();
        long countryCount = cities.values().stream()
                .map(TravelPlace::getCountryCode)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        return new BoardResponse.BoardTrip(trip.getId(), trip.getTitle(),
                trip.getStartDate(), trip.getEndDate(), status,
                status == TripStatus.COMPLETED,
                (int) cityCount, (int) countryCount, cityCount <= 1);
    }

    /** 선택한 날짜의 기준 도시와 견줘 일정마다 도시 이탈 여부를 붙인다. */
    private static List<ActivityResponse> withBaseCity(List<ActivityResponse> activities,
                                                       TravelPlace baseCity) {
        String ref = baseCity == null ? null : baseCity.getCityPlaceRef();
        return activities.stream().map(activity -> activity.withBaseCity(ref)).toList();
    }

    /**
     * 날짜를 안 주면 — 여행 중이면 <b>기준 도시 타임존의 오늘</b>, 아니면 1일차를 연다.
     * 현지에서 앱을 열자마자 오늘 일정이 보여야 하고, 그 "오늘"은 기기 시간대가 아니다.
     */
    private LocalDate resolveSelectedDate(Trip trip, TripStatus status, LocalDate requested,
                                          Map<LocalDate, TravelPlace> cities) {
        if (requested != null) {
            if (!trip.covers(requested)) {
                throw new CustomException(ErrorCode.TRAVEL_DATE_OUT_OF_RANGE);
            }
            return requested;
        }
        // 진행 중이면 "오늘"을 연다 — 그 오늘은 날짜마다 다른 시계로 정해진 값이다.
        return status == TripStatus.ONGOING
                ? TripClock.today(trip, cities, clock) : trip.getStartDate();
    }

    /**
     * 기간에서 날짜 탭을 만든다. 일정이 하나도 없는 날짜도 탭은 나와야 한다.
     *
     * <p>날짜마다 기준 도시와 구간 번호가 붙는다. {@code cityChanged}는 <b>직전 날짜와 도시가
     * 다른가</b>이고, 화면은 그 자리에 구분선을 긋는다 — 도시가 바뀌는 지점이 한눈에 보여야
     * 며칠씩 이어지는 일정에서 길을 잃지 않는다.
     */
    private List<BoardResponse.BoardDay> buildDays(Trip trip,
                                                   Map<LocalDate, TravelPlace> cities,
                                                   StayBoardAssembler.Stays stays) {
        Map<LocalDate, Long> counts = activityRepository.countByDate(trip.getId()).stream()
                .collect(Collectors.toMap(ActivityDateCount::activityDate, ActivityDateCount::count));
        // 날짜 탭이 전부 필요로 하므로 여행 기간을 한 번에 받는다(§S-08).
        // 도시별로 한 번씩만 조회한다 — 열흘짜리 여행이 열 번을 부르면 안 된다.
        Map<LocalDate, WeatherResponse.DailyWeather> weather =
                weatherService.dailyByDate(trip, cities);
        List<TripDay> dayRows = dayRepository.findAllByTripIdOrderByDayDateAsc(trip.getId());
        Map<LocalDate, Integer> legIndexes = legIndexByDate(dayRows);

        List<BoardResponse.BoardDay> days = new ArrayList<>();
        Long previousCity = null;
        for (TripDay row : dayRows) {
            LocalDate date = row.getDayDate();
            TravelPlace city = cities.get(date);
            days.add(new BoardResponse.BoardDay(
                    row.getId(),
                    trip.dayNumberOf(date),
                    date,
                    date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                    counts.getOrDefault(date, 0L),
                    city == null ? null : BaseCityResponse.from(city),
                    // 첫날은 "바뀐 것"이 아니다 — 비교할 앞 날짜가 없다.
                    previousCity != null && !previousCity.equals(row.getBasePlaceId()),
                    legIndexes.getOrDefault(date, 1),
                    row.getCityMemo(),
                    // 예보 범위(오늘부터 16일) 밖이면 null이다 — 화면이 그 자리를 비운다.
                    weather.get(date),
                    stayAssembler.tonight(stays, date, city),
                    stayAssembler.checkout(stays, date)));
            previousCity = row.getBasePlaceId();
        }
        return days;
    }

    /** 날짜가 몇 번째 구간에 속하는지 — 구간은 저장하지 않고 날짜에서 파생한다(D-21). */
    private static Map<LocalDate, Integer> legIndexByDate(List<TripDay> days) {
        Map<LocalDate, Integer> byDate = new HashMap<>();
        for (LegDeriver.DerivedLeg leg : LegDeriver.derive(days)) {
            for (LocalDate date = leg.startDate();
                    !date.isAfter(leg.endDate()); date = date.plusDays(1)) {
                byDate.put(date, leg.legIndex());
            }
        }
        return byDate;
    }
}
