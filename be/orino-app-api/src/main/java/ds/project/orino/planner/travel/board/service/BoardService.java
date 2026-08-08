package ds.project.orino.planner.travel.board.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.planner.travel.entity.TripStatus;
import ds.project.orino.domain.planner.travel.repository.ActivityDateCount;
import ds.project.orino.domain.planner.travel.repository.TripActivityRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.activity.service.ActivityService;
import ds.project.orino.planner.travel.board.dto.BoardResponse;
import ds.project.orino.planner.travel.route.service.LegService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
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
    private final LegService legService;
    private final Clock clock;

    public BoardService(TripRepository tripRepository,
                        TripActivityRepository activityRepository,
                        ActivityService activityService,
                        LegService legService,
                        Clock clock) {
        this.tripRepository = tripRepository;
        this.activityRepository = activityRepository;
        this.activityService = activityService;
        this.legService = legService;
        this.clock = clock;
    }

    /**
     * @param date    볼 날짜. {@code null}이면 서버가 고른다(아래 {@code defaultDate})
     * @param archive {@code true}면 날짜 대신 미배정 보관함을 본다
     */
    public BoardResponse board(Long memberId, Long tripId, LocalDate date, boolean archive) {
        Trip trip = tripRepository.findByIdAndMemberId(tripId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_TRIP_NOT_FOUND));
        TripStatus status = trip.status(clock);

        LocalDate selectedDate = archive ? null : resolveSelectedDate(trip, status, date);
        List<TripActivity> activities = selectedDate == null
                ? activityRepository.findUnscheduled(tripId)
                : activityRepository.findAllByTripIdAndActivityDateOrderBySortOrderAscIdAsc(
                        tripId, selectedDate);

        return new BoardResponse(
                new BoardResponse.BoardTrip(trip.getId(), trip.getTitle(), trip.getTimezone(),
                        trip.getCurrency(), trip.getStartDate(), trip.getEndDate(), status,
                        status == TripStatus.COMPLETED),
                buildDays(trip),
                selectedDate,
                activityRepository.countUnscheduled(tripId),
                activityService.toResponses(activities),
                legService.legs(activities));
    }

    /**
     * 날짜를 안 주면 — 여행 중이면 <b>여행 타임존의 오늘</b>, 아니면 1일차를 연다.
     * 현지에서 앱을 열자마자 오늘 일정이 보여야 하고, 그 "오늘"은 기기 시간대가 아니다.
     */
    private LocalDate resolveSelectedDate(Trip trip, TripStatus status, LocalDate requested) {
        if (requested != null) {
            if (!trip.covers(requested)) {
                throw new CustomException(ErrorCode.TRAVEL_DATE_OUT_OF_RANGE);
            }
            return requested;
        }
        return status == TripStatus.ONGOING ? trip.todayAtDestination(clock) : trip.getStartDate();
    }

    /** 기간에서 날짜 탭을 만든다. 일정이 하나도 없는 날짜도 탭은 나와야 한다. */
    private List<BoardResponse.BoardDay> buildDays(Trip trip) {
        Map<LocalDate, Long> counts = activityRepository.countByDate(trip.getId()).stream()
                .collect(Collectors.toMap(ActivityDateCount::activityDate, ActivityDateCount::count));

        List<BoardResponse.BoardDay> days = new ArrayList<>();
        for (int dayIndex = 1; dayIndex <= trip.totalDays(); dayIndex++) {
            LocalDate date = trip.getStartDate().plusDays(dayIndex - 1L);
            days.add(new BoardResponse.BoardDay(dayIndex, date,
                    date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                    counts.getOrDefault(date, 0L),
                    // 날씨는 4단계. 예보 범위 밖이면 그때도 null이 나간다.
                    null));
        }
        return days;
    }
}
