package ds.project.orino.planner.travel.trip.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.planner.travel.entity.TripStatus;
import ds.project.orino.domain.planner.travel.repository.TripActivityCount;
import ds.project.orino.domain.planner.travel.repository.TripActivityRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.day.service.BaseCityResolver;
import ds.project.orino.planner.travel.day.service.LegExpander;
import ds.project.orino.planner.travel.day.service.TripClock;
import ds.project.orino.planner.travel.expense.dto.ExpenseSummary;
import ds.project.orino.planner.travel.expense.service.TripExpenseQueryService;
import ds.project.orino.planner.travel.prep.dto.PrepSummary;
import ds.project.orino.planner.travel.prep.service.PrepService;
import ds.project.orino.planner.travel.day.service.TripDayService;
import ds.project.orino.planner.travel.day.service.TripStayShrinkService;
import ds.project.orino.planner.travel.trip.dto.ShrinkPreviewResponse;
import ds.project.orino.planner.travel.trip.dto.TravelSummaryResponse;
import ds.project.orino.planner.travel.trip.dto.TripCitySummary;
import ds.project.orino.planner.travel.trip.dto.TripDetail;
import ds.project.orino.planner.travel.trip.dto.TripListResponse;
import ds.project.orino.planner.travel.trip.dto.TripSummary;
import ds.project.orino.planner.travel.push.service.NotificationScheduleService;
import ds.project.orino.planner.travel.trip.dto.TripWriteRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 여행 CRUD와 기간 단축 처리.
 *
 * <p>상태(예정/진행 중/완료)와 D-day는 <b>저장하지 않고 매 조회 시 파생한다</b>. 기준은 기기
 * 시간대가 아니라 <b>첫날 기준 도시</b>의 타임존이라, 목록에서도 여행마다 따로 판정한다
 * (도쿄 여행과 하와이 여행이 같은 순간에 서로 다른 "오늘"을 갖는다).
 *
 * <p>기간을 줄이면 잘려나간 일정을 <b>지우지 않고 보관함으로 옮긴다</b>. 사용자가 모르고 일정을
 * 잃는 일이 없도록, 확인({@code confirmArchive}) 없이 들어온 단축 요청은 409로 되돌려보낸다.
 */
@Service
@Transactional(readOnly = true)
public class TripService {

    private final TripRepository tripRepository;
    private final TripActivityRepository activityRepository;
    private final NotificationScheduleService notificationService;
    private final TripDayService tripDayService;
    private final BaseCityResolver baseCityResolver;
    private final TripStayShrinkService stayShrinkService;
    private final PrepService prepService;
    private final TripExpenseQueryService expenseQueryService;
    private final Clock clock;

    public TripService(TripRepository tripRepository,
                       TripActivityRepository activityRepository,
                       NotificationScheduleService notificationService,
                       TripDayService tripDayService,
                       BaseCityResolver baseCityResolver,
                       TripStayShrinkService stayShrinkService,
                       PrepService prepService,
                       TripExpenseQueryService expenseQueryService,
                       Clock clock) {
        this.tripRepository = tripRepository;
        this.activityRepository = activityRepository;
        this.notificationService = notificationService;
        this.tripDayService = tripDayService;
        this.baseCityResolver = baseCityResolver;
        this.stayShrinkService = stayShrinkService;
        this.prepService = prepService;
        this.expenseQueryService = expenseQueryService;
        this.clock = clock;
    }

    @Transactional
    public TripDetail create(Long memberId, TripWriteRequest request) {
        requireValidPeriod(request.startDate(), request.endDate());
        Trip trip = new Trip(memberId, request.title().trim(),
                request.startDate(), request.endDate());
        applyOptionalSettings(trip, request);
        Trip saved = tripRepository.save(trip);
        tripRepository.flush();
        // 날짜 행이 없으면 타임존이 없는 여행이 된다 — 같은 트랜잭션에서 반드시 채운다.
        tripDayService.applyPlan(saved, expandLegs(memberId, saved, request));
        // 아침 요약은 일정이 아니라 날짜에 매달려 있어, 일정이 하나도 없어도 지금 잡힌다(§4.3).
        notificationService.rescheduleTrip(saved.getId());
        return detailOf(saved);
    }

    public TripListResponse list(Long memberId, TripStatus status) {
        List<Trip> all = tripRepository.findAllByMemberIdOrderByStartDateDescIdDesc(memberId);
        // 여행마다 자기 기준 도시의 오늘로 판정한다 — 도쿄 여행과 하와이 여행이 같은 순간에
        // 서로 다른 "오늘"을 갖는다. 도시는 여행 수만큼 조회하지 않고 한 번에 받아 둔다.
        TripCities cities = citiesOf(all);
        // 건수는 필터와 무관하게 전체 기준으로 센다 — 탭을 옮길 때마다 다른 탭 숫자가 0이 되면 안 된다.
        TripListResponse.TripCounts counts = countByStatus(all, cities);

        List<Trip> filtered = status == null ? all
                : all.stream().filter(trip -> statusOf(trip, cities) == status).toList();
        List<Trip> sorted = sortForDisplay(filtered, cities);

        Map<Long, Long> activityCounts = activityCountsOf(sorted);
        List<TripSummary> summaries = sorted.stream()
                .map(trip -> summaryOf(trip, cities,
                        activityCounts.getOrDefault(trip.getId(), 0L)))
                .toList();
        return new TripListResponse(counts, summaries);
    }

    public TripDetail detail(Long memberId, Long tripId) {
        return detailOf(getOwned(memberId, tripId));
    }

    /**
     * 전체 수정. 기간이 줄어 잘리는 일정이 있으면 확인을 요구하고, 확인이 왔을 때만
     * 보관함으로 옮긴다. 확인 요청(409)에는 이동 예정 건수를 실어 보낸다.
     */
    @Transactional
    public TripDetail update(Long memberId, Long tripId, TripWriteRequest request) {
        requireValidPeriod(request.startDate(), request.endDate());
        Trip trip = getOwned(memberId, tripId);

        long movedCount = activityRepository.countOutsidePeriod(
                tripId, request.startDate(), request.endDate());
        if (movedCount > 0 && !request.archiveConfirmed()) {
            TripStayShrinkService.Impact stays =
                    stayShrinkService.preview(tripId, request.endDate());
            throw CustomException.withData(ErrorCode.TRAVEL_ARCHIVE_CONFIRM_REQUIRED,
                    new ShrinkPreviewResponse(movedCount,
                            stays.shrunkCount(), stays.removedCount()));
        }

        // 기준 도시가 바뀌어도 일정의 벽시계 시각은 건드리지 않는다 — 09:00은 어디서든 09:00이다.
        trip.update(request.title().trim(), request.startDate(), request.endDate());
        applyOptionalSettings(trip, request);

        if (movedCount > 0) {
            archiveActivitiesOutsidePeriod(trip);
        }
        // 기간이 바뀌었으면 날짜 집합도 같이 움직여야 한다. 구간을 보냈으면 그대로 다시 펴고,
        // 생략했으면 도시 배치는 두고 기간만 맞춘다(새 날짜는 앞 날짜 도시를 상속).
        if (request.hasLegs()) {
            tripDayService.applyPlan(trip, expandLegs(memberId, trip, request));
        } else {
            tripDayService.syncPeriod(trip, tripDayService.primaryCity(tripId).getId());
        }
        // 걸쳐 있던 숙소는 체크아웃일을 새 종료일로 당기고, 묵는 밤이 없어지면 지운다.
        stayShrinkService.apply(tripId, request.endDate());
        tripRepository.flush();
        // §4.2 — 타임존이 바뀌면 벽시계 시각은 그대로고 알림 시각만 전부 다시 계산된다.
        // 기본 알림 시점·기간 변경도 같은 재계산으로 흡수된다.
        notificationService.rescheduleTrip(trip.getId());
        return detailOf(trip);
    }

    /** 기간을 이렇게 바꾸면 무엇이 밀려나는지. 생략한 날짜는 현재 값을 쓴다. */
    public ShrinkPreviewResponse shrinkPreview(Long memberId, Long tripId,
                                               LocalDate startDate, LocalDate endDate) {
        Trip trip = getOwned(memberId, tripId);
        LocalDate newStart = startDate != null ? startDate : trip.getStartDate();
        LocalDate newEnd = endDate != null ? endDate : trip.getEndDate();
        requireValidPeriod(newStart, newEnd);
        TripStayShrinkService.Impact stays = stayShrinkService.preview(tripId, newEnd);
        return new ShrinkPreviewResponse(
                activityRepository.countOutsidePeriod(tripId, newStart, newEnd),
                stays.shrunkCount(), stays.removedCount());
    }

    /** 여행 삭제 — 일정은 FK CASCADE로 함께 정리된다. */
    @Transactional
    public void delete(Long memberId, Long tripId) {
        tripRepository.delete(getOwned(memberId, tripId));
    }

    /**
     * `/select` 카드와 여행 홈(S-01)이 함께 쓰는 요약. 앞의 세 자리는 셋 다 없으면 전부 null이다.
     *
     * <p>(v2.2) 사이드바 여행 트리·폴백 화면이 읽는 {@code trips[]}가 여기서 함께 나온다 —
     * <b>같은 배열을 두 화면이 읽는다</b>. 따로 만들면 사이드바에는 있는데 폴백에는 없는
     * 여행이 생기고, 그 여행은 고를 수 없는 채로 사이드바에 남는다.
     */
    public TravelSummaryResponse summary(Long memberId) {
        List<Trip> all = tripRepository.findAllByMemberIdOrderByStartDateDescIdDesc(memberId);
        TripCities cities = citiesOf(all);

        Trip ongoing = all.stream()
                .filter(trip -> statusOf(trip, cities) == TripStatus.ONGOING)
                .min(Comparator.comparing(Trip::getStartDate).thenComparing(Trip::getId))
                .orElse(null);
        Trip next = all.stream()
                .filter(trip -> statusOf(trip, cities) == TripStatus.UPCOMING)
                .min(Comparator.comparing(Trip::getStartDate).thenComparing(Trip::getId))
                .orElse(null);
        Trip completed = all.stream()
                .filter(trip -> statusOf(trip, cities) == TripStatus.COMPLETED)
                .max(Comparator.comparing(Trip::getEndDate).thenComparing(Trip::getId))
                .orElse(null);

        Map<Long, Long> counts = activityCountsOf(
                Stream.of(ongoing, next, completed).filter(Objects::nonNull).toList());

        return new TravelSummaryResponse(
                ongoing == null ? null
                        : TravelSummaryResponse.OngoingTrip.of(ongoing.getId(),
                                ongoing.getTitle(), ongoing.getStartDate(),
                                ongoing.getEndDate(),
                                counts.getOrDefault(ongoing.getId(), 0L),
                                citySummaryOf(ongoing, cities),
                                prepSummaryOf(ongoing, cities)),
                next == null ? null : TravelSummaryResponse.NextTrip.of(
                        next.getId(), next.getTitle(), cityNameOf(next, cities),
                        next.getStartDate(), next.getEndDate(),
                        daysUntilStart(next, cities),
                        counts.getOrDefault(next.getId(), 0L),
                        citySummaryOf(next, cities),
                        prepSummaryOf(next, cities)),
                completed == null ? null : new TravelSummaryResponse.CompletedTrip(
                        completed.getId(), completed.getTitle(), completed.getEndDate(),
                        counts.getOrDefault(completed.getId(), 0L)),
                sidebarTripsOf(all, cities),
                (int) all.stream()
                        .filter(trip -> statusOf(trip, cities) == TripStatus.COMPLETED)
                        .count());
    }

    // ---------------- helpers ----------------

    /**
     * 사이드바가 펼치는 여행 — <b>진행 중 → 예정</b>, 각각 시작일 오름차순. 다녀온 여행은
     * 들어가지 않는다(D-39).
     *
     * <p>진행 중을 앞에 두는 이유는 사이드바가 <b>지금 어디에 있나</b>부터 답하는 자리이기
     * 때문이다. 시작일만으로 한 줄로 세우면 어제 시작한 여행이 다음 주 여행 뒤로 갈 수 있다.
     *
     * <p>준비·경비는 <b>여행 수에 비례하는 유일한 집계</b>다. 여행마다 부르지 않고 두 번의
     * 일괄 조회로 끝낸다 — 진행 중·예정은 보통 두어 건이지만, N+1이면 그때부터 는다.
     */
    private List<TravelSummaryResponse.SidebarTrip> sidebarTripsOf(List<Trip> all,
                                                                  TripCities cities) {
        List<Trip> ongoing = sortedByStart(all, cities, TripStatus.ONGOING);
        List<Trip> upcoming = sortedByStart(all, cities, TripStatus.UPCOMING);
        List<Trip> trips = new ArrayList<>(ongoing);
        trips.addAll(upcoming);
        if (trips.isEmpty()) {
            return List.of();
        }

        // 준비의 기준 "오늘"은 D-day와 같은 시계다 — 여기서 한 번 내고 준비 집계에 넘긴다.
        Map<Long, LocalDate> todayByTrip = new LinkedHashMap<>();
        trips.forEach(trip -> todayByTrip.put(trip.getId(),
                trip.getStartDate().minusDays(daysUntilStart(trip, cities))));

        Map<Long, PrepSummary> preps = prepService.summariesOf(trips, todayByTrip);
        Map<Long, ExpenseSummary> expenses = expenseQueryService.summariesOf(trips);

        return trips.stream()
                .map(trip -> sidebarTripOf(trip, cities, preps, expenses))
                .toList();
    }

    private TravelSummaryResponse.SidebarTrip sidebarTripOf(
            Trip trip, TripCities cities,
            Map<Long, PrepSummary> preps, Map<Long, ExpenseSummary> expenses) {
        boolean ongoing = statusOf(trip, cities) == TripStatus.ONGOING;
        return new TravelSummaryResponse.SidebarTrip(
                trip.getId(), trip.getTitle(), statusOf(trip, cities),
                trip.getStartDate(), trip.getEndDate(),
                // 「4일차」와 「D-49」는 같은 자리를 나눠 쓴다 — 한쪽만 찬다.
                ongoing ? null : daysUntilStart(trip, cities),
                ongoing ? trip.dayNumberOf(TripClock.today(trip, cities.of(trip), clock)) : null,
                preps.get(trip.getId()), expenses.get(trip.getId()));
    }

    private List<Trip> sortedByStart(List<Trip> trips, TripCities cities, TripStatus status) {
        return trips.stream()
                .filter(trip -> statusOf(trip, cities) == status)
                .sorted(Comparator.comparing(Trip::getStartDate).thenComparing(Trip::getId))
                .toList();
    }

    private Trip getOwned(Long memberId, Long tripId) {
        // 소유권 실패도 404 — 403이면 "그 id의 여행은 존재한다"가 새어나간다.
        return tripRepository.findByIdAndMemberId(tripId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_TRIP_NOT_FOUND));
    }

    /**
     * 기간 밖 일정을 보관함 맨 뒤로 붙인다. 비운 날짜는 통째로 사라지므로 재인덱싱할 대상이
     * 없고, 보관함만 0..n-1이 이어지도록 순서를 새로 부여한다.
     */
    private void archiveActivitiesOutsidePeriod(Trip trip) {
        List<TripActivity> outside = activityRepository.findOutsidePeriod(
                trip.getId(), trip.getStartDate(), trip.getEndDate());
        int nextOrder = activityRepository.nextSortOrder(trip.getId(), null);
        for (TripActivity activity : outside) {
            activity.moveTo(null, nextOrder++);
        }
    }

    /**
     * 구간을 날짜로 편다. 도시 해석과 전개를 한 곳에 묶어 두는 이유는, 둘 사이에 다른 일이
     * 끼면 "저장은 됐는데 어떤 날짜에는 도시가 없는" 중간 상태가 생기기 때문이다.
     */
    private Map<LocalDate, Long> expandLegs(Long memberId, Trip trip, TripWriteRequest request) {
        List<TravelPlace> cities = baseCityResolver.resolveAll(memberId, request.legs());
        List<LegExpander.Leg> legs = new java.util.ArrayList<>();
        for (int i = 0; i < cities.size(); i++) {
            legs.add(new LegExpander.Leg(cities.get(i).getId(), request.legs().get(i).days()));
        }
        return LegExpander.expand(trip.getStartDate(), trip.getEndDate(), legs);
    }

    private void applyOptionalSettings(Trip trip, TripWriteRequest request) {
        // 생략된 값은 기존 설정을 유지한다(수정 화면이 알림 설정을 안 보낼 수 있다).
        int notifyMinutes = request.defaultNotifyMinutes() != null
                ? request.defaultNotifyMinutes() : trip.getDefaultNotifyMinutes();
        boolean morningSummary = request.morningSummaryEnabled() != null
                ? request.morningSummaryEnabled() : trip.isMorningSummaryEnabled();
        trip.updateNotificationSettings(notifyMinutes, morningSummary);
    }

    private void requireValidPeriod(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new CustomException(ErrorCode.TRAVEL_INVALID_PERIOD);
        }
    }

    /**
     * 목록·요약이 한 번에 받아 두는 도시 지도.
     *
     * @param byDate 여행 → (날짜 → 기준 도시). <b>상태</b>는 오늘에 해당하는 날짜로 판정한다
     * @param first  여행 → 첫날 기준 도시. <b>D-day·목적지 표시</b>가 쓴다
     */
    private record TripCities(Map<Long, Map<LocalDate, TravelPlace>> byDate,
                              Map<Long, TravelPlace> first) {

        Map<LocalDate, TravelPlace> of(Trip trip) {
            return byDate.getOrDefault(trip.getId(), Map.of());
        }
    }

    private TripCities citiesOf(List<Trip> trips) {
        List<Long> ids = trips.stream().map(Trip::getId).toList();
        return new TripCities(tripDayService.baseCitiesByTrip(ids),
                tripDayService.primaryCitiesOf(ids));
    }

    private String cityNameOf(Trip trip, TripCities cities) {
        TravelPlace city = cities.first().get(trip.getId());
        return city == null ? null : city.getName();
    }

    private TripStatus statusOf(Trip trip, TripCities cities) {
        return TripClock.status(trip, cities.of(trip), clock);
    }

    /**
     * 준비 진행률·기한 지남 개수. 기준 "오늘"은 <b>D-day와 같은 시계</b>로 낸다 —
     * {@code daysUntilStart}가 첫날 기준 도시로 센 값이므로 출발일에서 빼면 그 도시의 오늘이다.
     * 여기서 서버 로컬 날짜를 쓰면 출발 전날 밤에 카드의 D-day와 기한 경고가 하루 어긋난다.
     */
    private PrepSummary prepSummaryOf(Trip trip, TripCities cities) {
        LocalDate today = trip.getStartDate().minusDays(daysUntilStart(trip, cities));
        return prepService.summaryOf(trip, today);
    }

    private long daysUntilStart(Trip trip, TripCities cities) {
        return TripClock.daysUntilStart(trip, cities.of(trip), clock);
    }

    /**
     * 표시 순서 — 예정·진행 중은 다가오는 순(시작일 오름차순), 완료는 최근 순(종료일 내림차순).
     * 방향이 반대라 하나의 Comparator로 묶지 않고 두 덩어리로 나눠 이어 붙인다.
     * 필터 없이 전체를 볼 때는 앞으로 갈 여행이 먼저, 지나간 여행이 뒤로 온다.
     */
    private List<Trip> sortForDisplay(List<Trip> trips, TripCities cities) {
        Stream<Trip> upcoming = trips.stream()
                .filter(trip -> statusOf(trip, cities) != TripStatus.COMPLETED)
                .sorted(Comparator.comparing(Trip::getStartDate).thenComparing(Trip::getId));
        Stream<Trip> completed = trips.stream()
                .filter(trip -> statusOf(trip, cities) == TripStatus.COMPLETED)
                .sorted(Comparator.comparing(Trip::getEndDate).reversed()
                        .thenComparing(Trip::getId));
        return Stream.concat(upcoming, completed).toList();
    }

    private Map<Long, Long> activityCountsOf(List<Trip> trips) {
        if (trips.isEmpty()) {
            return Map.of();
        }
        List<Long> tripIds = trips.stream().map(Trip::getId).toList();
        return activityRepository.countByTripIds(tripIds).stream()
                .collect(Collectors.toMap(TripActivityCount::tripId, TripActivityCount::count));
    }

    private TripSummary summaryOf(Trip trip, TripCities cities, long activityCount) {
        return new TripSummary(trip.getId(), trip.getTitle(), cityNameOf(trip, cities),
                trip.getStartDate(), trip.getEndDate(), statusOf(trip, cities),
                daysUntilStart(trip, cities), activityCount, citySummaryOf(trip, cities));
    }

    /**
     * 도시 나열과 오늘의 도시. <b>추가 조회가 없다</b> — 목록·요약은 상태 판정을 위해 이미
     * 모든 여행의 날짜별 기준 도시를 한 번에 받아 두었고, 여기서는 그 지도를 접기만 한다.
     * 여행마다 {@code /city-legs}를 부르면 그때부터 N+1이다.
     *
     * <p>오늘 관련 값은 <b>진행 중일 때만</b> 채운다. 예정 여행에 "오늘의 도시"를 주면 첫날
     * 도시가 오늘인 것처럼 보인다.
     */
    private TripCitySummary citySummaryOf(Trip trip, TripCities cities) {
        Map<LocalDate, TravelPlace> byDate = cities.of(trip);
        if (byDate.isEmpty()) {
            return TripCitySummary.empty();
        }
        LocalDate today = statusOf(trip, cities) == TripStatus.ONGOING
                ? TripClock.today(trip, byDate, clock) : null;
        return TripCitySummary.of(trip.getStartDate(), trip.getEndDate(), byDate, today);
    }

    /**
     * 상세는 여행 하나라 첫날 기준 도시를 바로 읽는다. 목적지 자리에 그 도시의 이름·타임존·
     * 통화·좌표가 그대로 들어간다 — v2.0의 목적지가 첫날의 기준 도시로 내려온 것이라,
     * 단일 도시 여행에서는 응답이 전과 같다.
     */
    private TripDetail detailOf(Trip trip) {
        Map<LocalDate, TravelPlace> byDate = tripDayService.baseCitiesOf(trip.getId());
        TravelPlace city = tripDayService.primaryCity(trip.getId());
        return new TripDetail(trip.getId(), trip.getTitle(), city.getName(),
                city.getId(), trip.getStartDate(), trip.getEndDate(),
                city.getTimezone(), city.getCurrency(), city.getLat(), city.getLng(),
                trip.getDefaultNotifyMinutes(), trip.isMorningSummaryEnabled(),
                TripClock.status(trip, byDate, clock),
                TripClock.daysUntilStart(trip, byDate, clock), trip.totalDays(),
                activityRepository.countByTripId(trip.getId()));
    }

    private TripListResponse.TripCounts countByStatus(List<Trip> trips, TripCities cities) {
        Map<TripStatus, Long> byStatus = trips.stream()
                .collect(Collectors.groupingBy(trip -> statusOf(trip, cities),
                        Collectors.counting()));
        return new TripListResponse.TripCounts(
                byStatus.getOrDefault(TripStatus.UPCOMING, 0L),
                byStatus.getOrDefault(TripStatus.ONGOING, 0L),
                byStatus.getOrDefault(TripStatus.COMPLETED, 0L));
    }
}
