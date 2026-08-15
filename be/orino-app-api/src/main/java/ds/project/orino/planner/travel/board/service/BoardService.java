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
import ds.project.orino.planner.travel.day.service.TransitionDays;
import ds.project.orino.planner.travel.day.service.TripClock;
import ds.project.orino.planner.travel.day.service.TripDayService;
import ds.project.orino.planner.travel.move.service.MoveService;
import ds.project.orino.planner.travel.stay.service.StayBoardAssembler;
import ds.project.orino.planner.travel.tools.service.WeatherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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
    private final MoveService moveService;
    private final WeatherService weatherService;
    private final StayBoardAssembler stayAssembler;
    private final Clock clock;

    public BoardService(TripRepository tripRepository,
                        TripActivityRepository activityRepository,
                        TripDayRepository dayRepository,
                        ActivityService activityService,
                        TripDayService tripDayService,
                        MoveService moveService,
                        WeatherService weatherService,
                        StayBoardAssembler stayAssembler,
                        Clock clock) {
        this.tripRepository = tripRepository;
        this.activityRepository = activityRepository;
        this.dayRepository = dayRepository;
        this.activityService = activityService;
        this.tripDayService = tripDayService;
        this.moveService = moveService;
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

        List<TripDay> dayRows = dayRepository.findAllByTripIdOrderByDayDateAsc(trip.getId());
        // 도시가 바뀌는 날 → 떠나온 도시. 날짜 탭과 도시 이탈 판정이 같은 파생을 쓴다.
        Map<LocalDate, TravelPlace> departedCities = departedCitiesOf(dayRows, cities);

        // 선택한 날짜에 있어도 되는 도시들. 이동일이면 떠나온 도시도 함께 통과시킨다(D-25).
        TravelPlace selectedCity = selectedDate == null ? null : cities.get(selectedDate);
        TravelPlace departedCity = selectedDate == null ? null : departedCities.get(selectedDate);
        // 숙소는 여행 전체를 한 번에 읽는다 — 날짜 탭마다 조회하면 기간만큼 쿼리가 는다.
        StayBoardAssembler.Stays stays = stayAssembler.of(tripId);

        return new BoardResponse(
                buildTrip(trip, cities, status),
                buildDays(trip, dayRows, cities, departedCities, stays),
                selectedDate,
                activityRepository.countUnscheduled(tripId),
                withCityRules(activityService.toResponses(activities),
                        TransitionDays.cityRefsOf(selectedCity, departedCity),
                        // 보관함 일정은 날짜가 없어 출발 알림 시각 자체가 서지 않는다.
                        selectedDate == null ? Set.of()
                                : moveService.departureNotifiable(activities)),
                // 보관함은 날짜에 배정되지 않은 목록이라 순서에 이동 의미가 없다.
                selectedDate == null ? List.of() : moveService.moves(memberId, activities),
                // 보관함에는 "그날 밤"이 없다 — 숙소 이동도 없다.
                selectedDate == null ? null
                        : stayAssembler.moveToStay(memberId, stays, selectedDate, activities));
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

    /**
     * 일정마다 도시 관련 판정을 붙인다 — 도시 이탈, 그리고 출발 알림 가능 여부.
     *
     * <p>둘 다 <b>그날 전체를 봐야</b> 나오는 값이라 일정 하나를 조립할 때는 알 수 없다.
     * 조립을 마친 뒤 덧씌운다.
     *
     * @param cityRefs 그날 있어도 되는 도시들. 이동일이면 둘이다(D-25)
     */
    private static List<ActivityResponse> withCityRules(List<ActivityResponse> activities,
                                                        Set<String> cityRefs,
                                                        Set<Long> departureNotifiable) {
        return activities.stream()
                .map(activity -> activity.withBaseCities(cityRefs)
                        .withCanDepartureNotify(departureNotifiable.contains(activity.id())))
                .toList();
    }

    /**
     * 도시가 바뀌는 날 → 떠나온 도시. {@link TransitionDays}가 장소 id로 답하는 것을 그날의
     * 장소로 풀어 둔다 — 날짜 탭과 일정 판정이 같은 값을 쓴다.
     *
     * <p>이미 읽어 둔 {@code cities}로 장소를 푼다 — 날짜 탭이 쓰는 도시와 같은 객체라
     * 조회가 늘지 않는다. {@code cityChanged}도 이 맵의 {@code containsKey}로 답한다.
     */
    private static Map<LocalDate, TravelPlace> departedCitiesOf(
            List<TripDay> dayRows, Map<LocalDate, TravelPlace> cities) {
        Map<Long, TravelPlace> byPlaceId = cities.values().stream()
                .collect(Collectors.toMap(TravelPlace::getId, Function.identity(),
                        (kept, duplicate) -> kept));
        Map<LocalDate, TravelPlace> departed = new LinkedHashMap<>();
        TransitionDays.departedByDate(dayRows).forEach((date, placeId) -> {
            TravelPlace city = byPlaceId.get(placeId);
            if (city != null) {
                departed.put(date, city);
            }
        });
        return departed;
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
     *
     * <p>그 날짜에는 <b>떠나온 도시도 함께</b> 붙는다(D-25). 탭이 {@code 오사카 → 교토}로
     * 그려지고 날씨도 두 도시가 나온다 — 이동일 오전은 아직 그 도시에 있기 때문이다.
     */
    private List<BoardResponse.BoardDay> buildDays(Trip trip,
                                                   List<TripDay> dayRows,
                                                   Map<LocalDate, TravelPlace> cities,
                                                   Map<LocalDate, TravelPlace> departedCities,
                                                   StayBoardAssembler.Stays stays) {
        Map<LocalDate, Long> counts = activityRepository.countByDate(trip.getId()).stream()
                .collect(Collectors.toMap(ActivityDateCount::activityDate, ActivityDateCount::count));
        // 날짜 탭이 전부 필요로 하므로 여행 기간을 한 번에 받는다(§S-08).
        // 도시별로 한 번씩만 조회한다 — 열흘짜리 여행이 열 번을 부르면 안 된다.
        WeatherService.DailyForecasts weather =
                weatherService.dailyByDate(cities, departedCities);
        Map<LocalDate, Integer> legIndexes = legIndexByDate(dayRows);

        List<BoardResponse.BoardDay> days = new ArrayList<>();
        for (TripDay row : dayRows) {
            LocalDate date = row.getDayDate();
            TravelPlace city = cities.get(date);
            // 도시가 바뀐 날에만 키가 있다 — cityChanged와 arrivingFrom이 한 파생에서 나온다.
            TravelPlace from = departedCities.get(date);
            days.add(new BoardResponse.BoardDay(
                    row.getId(),
                    trip.dayNumberOf(date),
                    date,
                    date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                    counts.getOrDefault(date, 0L),
                    city == null ? null : BaseCityResponse.from(city),
                    departedCities.containsKey(date),
                    from == null ? null : BaseCityResponse.from(from),
                    legIndexes.getOrDefault(date, 1),
                    row.getCityMemo(),
                    // 예보 범위(오늘부터 16일) 밖이면 null이다 — 화면이 그 자리를 비운다.
                    weather.arrived().get(date),
                    weather.departed().get(date),
                    stayAssembler.tonight(stays, date, city),
                    stayAssembler.checkout(stays, date)));
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
