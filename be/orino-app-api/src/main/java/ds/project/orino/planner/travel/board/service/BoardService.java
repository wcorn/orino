package ds.project.orino.planner.travel.board.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.planner.travel.entity.TripStatus;
import ds.project.orino.domain.planner.travel.repository.ActivityDateCount;
import ds.project.orino.domain.planner.travel.repository.TripActivityRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.activity.service.ActivityService;
import ds.project.orino.planner.travel.board.dto.BoardResponse;
import ds.project.orino.planner.travel.day.service.TripDayService;
import ds.project.orino.planner.travel.route.service.LegService;
import ds.project.orino.planner.travel.tools.dto.WeatherResponse;
import ds.project.orino.planner.travel.tools.service.WeatherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final ActivityService activityService;
    private final TripDayService tripDayService;
    private final LegService legService;
    private final WeatherService weatherService;
    private final Clock clock;

    public BoardService(TripRepository tripRepository,
                        TripActivityRepository activityRepository,
                        ActivityService activityService,
                        TripDayService tripDayService,
                        LegService legService,
                        WeatherService weatherService,
                        Clock clock) {
        this.tripRepository = tripRepository;
        this.activityRepository = activityRepository;
        this.activityService = activityService;
        this.tripDayService = tripDayService;
        this.legService = legService;
        this.weatherService = weatherService;
        this.clock = clock;
    }

    /**
     * @param date    볼 날짜. {@code null}이면 서버가 고른다(아래 {@code defaultDate})
     * @param archive {@code true}면 날짜 대신 미배정 보관함을 본다
     */
    public BoardResponse board(Long memberId, Long tripId, LocalDate date, boolean archive) {
        Trip trip = tripRepository.findByIdAndMemberId(tripId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_TRIP_NOT_FOUND));
        // 헤더의 타임존·통화는 아직 첫날 기준 도시에서 온다. 선택한 날짜의 도시로 갈라내는 건
        // 파생 재작성(#1123)·보드 응답 v2.1(#1124)의 몫이다.
        TravelPlace city = tripDayService.primaryCity(tripId);
        TripStatus status = trip.status(clock, TripDayService.zoneOf(city));

        LocalDate selectedDate = archive ? null
                : resolveSelectedDate(trip, status, date, TripDayService.zoneOf(city));
        List<TripActivity> activities = selectedDate == null
                ? activityRepository.findUnscheduled(tripId)
                : activityRepository.findAllByTripIdAndActivityDateOrderBySortOrderAscIdAsc(
                        tripId, selectedDate);

        return new BoardResponse(
                new BoardResponse.BoardTrip(trip.getId(), trip.getTitle(), city.getTimezone(),
                        city.getCurrency(), trip.getStartDate(), trip.getEndDate(), status,
                        status == TripStatus.COMPLETED),
                buildDays(trip),
                selectedDate,
                activityRepository.countUnscheduled(tripId),
                activityService.toResponses(activities),
                // 보관함은 날짜에 배정되지 않은 목록이라 순서에 이동 의미가 없다.
                // 계산해 봐야 화면에 쓰지 않고, 호출당 과금이라 그냥 낭비다.
                selectedDate == null ? List.of() : legService.legs(activities));
    }

    /**
     * 날짜를 안 주면 — 여행 중이면 <b>기준 도시 타임존의 오늘</b>, 아니면 1일차를 연다.
     * 현지에서 앱을 열자마자 오늘 일정이 보여야 하고, 그 "오늘"은 기기 시간대가 아니다.
     */
    private LocalDate resolveSelectedDate(Trip trip, TripStatus status, LocalDate requested,
                                          ZoneId zone) {
        if (requested != null) {
            if (!trip.covers(requested)) {
                throw new CustomException(ErrorCode.TRAVEL_DATE_OUT_OF_RANGE);
            }
            return requested;
        }
        return status == TripStatus.ONGOING ? trip.todayIn(clock, zone) : trip.getStartDate();
    }

    /** 기간에서 날짜 탭을 만든다. 일정이 하나도 없는 날짜도 탭은 나와야 한다. */
    private List<BoardResponse.BoardDay> buildDays(Trip trip) {
        Map<LocalDate, Long> counts = activityRepository.countByDate(trip.getId()).stream()
                .collect(Collectors.toMap(ActivityDateCount::activityDate, ActivityDateCount::count));
        // 날짜 탭이 전부 필요로 하므로 여행 기간을 한 번에 받는다(§S-08).
        Map<LocalDate, WeatherResponse.DailyWeather> weather = weatherService.dailyByDate(trip);

        List<BoardResponse.BoardDay> days = new ArrayList<>();
        for (int dayIndex = 1; dayIndex <= trip.totalDays(); dayIndex++) {
            LocalDate date = trip.getStartDate().plusDays(dayIndex - 1L);
            days.add(new BoardResponse.BoardDay(dayIndex, date,
                    date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                    counts.getOrDefault(date, 0L),
                    // 예보 범위(오늘부터 16일) 밖이면 null이다 — 화면이 그 자리를 비운다.
                    weather.get(date)));
        }
        return days;
    }
}
