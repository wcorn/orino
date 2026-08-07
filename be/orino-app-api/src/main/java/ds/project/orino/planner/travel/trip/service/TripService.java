package ds.project.orino.planner.travel.trip.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.planner.travel.entity.TripStatus;
import ds.project.orino.domain.planner.travel.repository.TripActivityCount;
import ds.project.orino.domain.planner.travel.repository.TripActivityRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.trip.dto.ShrinkPreviewResponse;
import ds.project.orino.planner.travel.trip.dto.TravelSummaryResponse;
import ds.project.orino.planner.travel.trip.dto.TripDetail;
import ds.project.orino.planner.travel.trip.dto.TripListResponse;
import ds.project.orino.planner.travel.trip.dto.TripSummary;
import ds.project.orino.planner.travel.trip.dto.TripWriteRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 여행 CRUD와 기간 단축 처리.
 *
 * <p>상태(예정/진행 중/완료)와 D-day는 <b>저장하지 않고 매 조회 시 파생한다</b>. 기준은 기기
 * 시간대가 아니라 각 여행의 타임존이라, 목록에서도 여행마다 자기 타임존으로 따로 판정한다
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
    private final Clock clock;

    public TripService(TripRepository tripRepository,
                       TripActivityRepository activityRepository,
                       Clock clock) {
        this.tripRepository = tripRepository;
        this.activityRepository = activityRepository;
        this.clock = clock;
    }

    @Transactional
    public TripDetail create(Long memberId, TripWriteRequest request) {
        validate(request);
        Trip trip = new Trip(memberId, request.resolvedTitle(), request.destinationName().trim(),
                request.startDate(), request.endDate(), request.timezone(),
                normalizedCurrency(request.currency()));
        applyOptionalSettings(trip, request);
        return detailOf(tripRepository.save(trip));
    }

    public TripListResponse list(Long memberId, TripStatus status) {
        List<Trip> all = tripRepository.findAllByMemberIdOrderByStartDateDescIdDesc(memberId);
        // 건수는 필터와 무관하게 전체 기준으로 센다 — 탭을 옮길 때마다 다른 탭 숫자가 0이 되면 안 된다.
        TripListResponse.TripCounts counts = countByStatus(all);

        List<Trip> filtered = status == null ? all
                : all.stream().filter(trip -> statusOf(trip) == status).toList();
        List<Trip> sorted = sortForDisplay(filtered);

        Map<Long, Long> activityCounts = activityCountsOf(sorted);
        List<TripSummary> summaries = sorted.stream()
                .map(trip -> summaryOf(trip, activityCounts.getOrDefault(trip.getId(), 0L)))
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
        validate(request);
        Trip trip = getOwned(memberId, tripId);

        long movedCount = activityRepository.countOutsidePeriod(
                tripId, request.startDate(), request.endDate());
        if (movedCount > 0 && !request.archiveConfirmed()) {
            throw CustomException.withData(ErrorCode.TRAVEL_ARCHIVE_CONFIRM_REQUIRED,
                    new ShrinkPreviewResponse(movedCount));
        }

        // 타임존이 바뀌어도 일정의 벽시계 시각은 건드리지 않는다 — 09:00은 어디서든 09:00이다.
        trip.update(request.resolvedTitle(), request.destinationName().trim(),
                request.startDate(), request.endDate(), request.timezone(),
                normalizedCurrency(request.currency()));
        applyOptionalSettings(trip, request);

        if (movedCount > 0) {
            archiveActivitiesOutsidePeriod(trip);
        }
        return detailOf(trip);
    }

    /** 기간을 이렇게 바꾸면 몇 개가 보관함으로 가는지. 생략한 날짜는 현재 값을 쓴다. */
    public ShrinkPreviewResponse shrinkPreview(Long memberId, Long tripId,
                                               LocalDate startDate, LocalDate endDate) {
        Trip trip = getOwned(memberId, tripId);
        LocalDate newStart = startDate != null ? startDate : trip.getStartDate();
        LocalDate newEnd = endDate != null ? endDate : trip.getEndDate();
        requireValidPeriod(newStart, newEnd);
        return new ShrinkPreviewResponse(
                activityRepository.countOutsidePeriod(tripId, newStart, newEnd));
    }

    /** 여행 삭제 — 일정은 FK CASCADE로 함께 정리된다. */
    @Transactional
    public void delete(Long memberId, Long tripId) {
        tripRepository.delete(getOwned(memberId, tripId));
    }

    /** `/select` 카드와 여행 홈(S-01)이 함께 쓰는 요약. 셋 다 없으면 전부 null이다. */
    public TravelSummaryResponse summary(Long memberId) {
        List<Trip> all = tripRepository.findAllByMemberIdOrderByStartDateDescIdDesc(memberId);

        Trip ongoing = all.stream()
                .filter(trip -> statusOf(trip) == TripStatus.ONGOING)
                .min(Comparator.comparing(Trip::getStartDate).thenComparing(Trip::getId))
                .orElse(null);
        Trip next = all.stream()
                .filter(trip -> statusOf(trip) == TripStatus.UPCOMING)
                .min(Comparator.comparing(Trip::getStartDate).thenComparing(Trip::getId))
                .orElse(null);
        Trip completed = all.stream()
                .filter(trip -> statusOf(trip) == TripStatus.COMPLETED)
                .max(Comparator.comparing(Trip::getEndDate).thenComparing(Trip::getId))
                .orElse(null);

        Map<Long, Long> counts = activityCountsOf(
                Stream.of(next, completed).filter(Objects::nonNull).toList());

        return new TravelSummaryResponse(
                ongoing == null ? null
                        : TravelSummaryResponse.OngoingTrip.of(ongoing.getId(), ongoing.getTitle()),
                next == null ? null : new TravelSummaryResponse.NextTrip(
                        next.getId(), next.getTitle(), next.getDestinationName(),
                        next.getStartDate(), next.getEndDate(), next.daysUntilStart(clock),
                        counts.getOrDefault(next.getId(), 0L)),
                completed == null ? null : new TravelSummaryResponse.CompletedTrip(
                        completed.getId(), completed.getTitle(), completed.getEndDate(),
                        counts.getOrDefault(completed.getId(), 0L)));
    }

    // ---------------- helpers ----------------

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

    private void applyOptionalSettings(Trip trip, TripWriteRequest request) {
        if (request.destinationPlaceId() != null || request.lat() != null || request.lng() != null) {
            trip.updateDestinationPlace(request.destinationPlaceId(), request.lat(), request.lng());
        }
        // 생략된 값은 기존 설정을 유지한다(수정 화면이 알림 설정을 안 보낼 수 있다).
        int notifyMinutes = request.defaultNotifyMinutes() != null
                ? request.defaultNotifyMinutes() : trip.getDefaultNotifyMinutes();
        boolean morningSummary = request.morningSummaryEnabled() != null
                ? request.morningSummaryEnabled() : trip.isMorningSummaryEnabled();
        trip.updateNotificationSettings(notifyMinutes, morningSummary);
    }

    private void validate(TripWriteRequest request) {
        requireValidPeriod(request.startDate(), request.endDate());
        requireValidTimezone(request.timezone());
        requireValidCurrency(request.currency());
    }

    private void requireValidPeriod(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new CustomException(ErrorCode.TRAVEL_INVALID_PERIOD);
        }
    }

    /**
     * IANA 지역 ID만 받는다. {@code ZoneId.of}는 {@code "UTC+09:00"} 같은 오프셋도 통과시키는데,
     * 오프셋은 서머타임을 모르므로 알림 시각 환산이 계절에 따라 어긋난다.
     */
    private void requireValidTimezone(String timezone) {
        if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new CustomException(ErrorCode.TRAVEL_INVALID_TIMEZONE);
        }
    }

    private void requireValidCurrency(String currency) {
        try {
            Currency.getInstance(normalizedCurrency(currency));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.TRAVEL_INVALID_CURRENCY);
        }
    }

    private String normalizedCurrency(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private TripStatus statusOf(Trip trip) {
        return trip.status(clock);
    }

    /**
     * 표시 순서 — 예정·진행 중은 다가오는 순(시작일 오름차순), 완료는 최근 순(종료일 내림차순).
     * 방향이 반대라 하나의 Comparator로 묶지 않고 두 덩어리로 나눠 이어 붙인다.
     * 필터 없이 전체를 볼 때는 앞으로 갈 여행이 먼저, 지나간 여행이 뒤로 온다.
     */
    private List<Trip> sortForDisplay(List<Trip> trips) {
        Stream<Trip> upcoming = trips.stream()
                .filter(trip -> statusOf(trip) != TripStatus.COMPLETED)
                .sorted(Comparator.comparing(Trip::getStartDate).thenComparing(Trip::getId));
        Stream<Trip> completed = trips.stream()
                .filter(trip -> statusOf(trip) == TripStatus.COMPLETED)
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

    private TripSummary summaryOf(Trip trip, long activityCount) {
        return new TripSummary(trip.getId(), trip.getTitle(), trip.getDestinationName(),
                trip.getStartDate(), trip.getEndDate(), statusOf(trip),
                trip.daysUntilStart(clock), activityCount);
    }

    private TripDetail detailOf(Trip trip) {
        return new TripDetail(trip.getId(), trip.getTitle(), trip.getDestinationName(),
                trip.getDestinationPlaceId(), trip.getStartDate(), trip.getEndDate(),
                trip.getTimezone(), trip.getCurrency(), trip.getLat(), trip.getLng(),
                trip.getDefaultNotifyMinutes(), trip.isMorningSummaryEnabled(),
                statusOf(trip), trip.daysUntilStart(clock), trip.totalDays(),
                activityRepository.countByTripId(trip.getId()));
    }

    private TripListResponse.TripCounts countByStatus(List<Trip> trips) {
        Map<TripStatus, Long> byStatus = trips.stream()
                .collect(Collectors.groupingBy(this::statusOf, Collectors.counting()));
        return new TripListResponse.TripCounts(
                byStatus.getOrDefault(TripStatus.UPCOMING, 0L),
                byStatus.getOrDefault(TripStatus.ONGOING, 0L),
                byStatus.getOrDefault(TripStatus.COMPLETED, 0L));
    }
}
