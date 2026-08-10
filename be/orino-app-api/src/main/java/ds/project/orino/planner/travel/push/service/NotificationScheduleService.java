package ds.project.orino.planner.travel.push.service;

import ds.project.orino.domain.planner.push.entity.NotificationStatus;
import ds.project.orino.domain.planner.push.entity.NotificationType;
import ds.project.orino.domain.planner.push.entity.PushNotification;
import ds.project.orino.domain.planner.push.repository.PushNotificationRepository;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.planner.travel.repository.TripActivityRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.day.service.TripDayService;
import ds.project.orino.planner.travel.route.dto.TravelTimeResponse;
import ds.project.orino.planner.travel.route.service.TravelTimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 알림 예약과 재계산(§4.2).
 *
 * <p>트리거가 걸리면 <b>그 일정의 대기 중 알림을 전부 접고 다시 만든다.</b> 부분 수정하지 않는
 * 이유는 무엇이 바뀌었는지에 따라 만들 알림의 <b>종류와 개수가 달라지기</b> 때문이다 —
 * 시각을 지우면 둘 다 사라지고, 앞 일정에 장소가 생기면 출발 알림이 새로 생긴다.
 *
 * <p>취소는 <b>삭제가 아니라 상태 전이</b>다. 알림이 왜 그 시각에 갔는지, 혹은 왜 안 갔는지를
 * 나중에 추적할 수 있어야 한다.
 */
@Service
public class NotificationScheduleService {

    /** 아침 요약을 보내는 현지 시각(§4.3). */
    private static final int MORNING_SUMMARY_HOUR = 8;

    private final PushNotificationRepository notificationRepository;
    private final TripActivityRepository activityRepository;
    private final TripRepository tripRepository;
    private final TripDayService tripDayService;
    private final TravelTimeService travelTimeService;

    public NotificationScheduleService(PushNotificationRepository notificationRepository,
                                       TripActivityRepository activityRepository,
                                       TripRepository tripRepository,
                                       TripDayService tripDayService,
                                       TravelTimeService travelTimeService) {
        this.notificationRepository = notificationRepository;
        this.activityRepository = activityRepository;
        this.tripRepository = tripRepository;
        this.tripDayService = tripDayService;
        this.travelTimeService = travelTimeService;
    }

    /**
     * 한 날짜의 알림을 다시 짠다.
     *
     * <p>일정 하나가 아니라 <b>날짜 전체</b>를 다시 계산한다. 출발 알림은 직전 일정과의 이동시간에
     * 걸려 있어, 하나를 옮기면 그 뒤 일정의 출발 시각도 함께 바뀐다.
     */
    @Transactional
    public void rescheduleDate(Long tripId, LocalDate date) {
        if (date == null) {
            return;
        }
        Trip trip = tripRepository.findById(tripId).orElse(null);
        if (trip == null) {
            return;
        }
        List<TripActivity> ordered = activityRepository
                .findAllByTripIdAndActivityDateOrderBySortOrderAscIdAsc(tripId, date);

        ordered.forEach(activity -> cancelPending(activity.getId()));
        notificationRepository.saveAll(build(trip, ordered, tripDayService.zoneOn(tripId, date)));
    }

    /**
     * 여행 전체를 다시 짠다 — 기준 도시가 바뀌면 그 날짜부터의 환산 결과가 달라진다.
     *
     * <p><b>날짜마다 자기 기준 도시의 타임존으로 환산한다.</b> 여행 하나에 타임존 하나라고
     * 보면, 오사카에서 나고야로 넘어간 날의 09:00 알림이 오사카 시각으로 예약된다.
     */
    @Transactional
    public void rescheduleTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId).orElse(null);
        if (trip == null) {
            return;
        }
        cancelAll(notificationRepository.findAllByTripIdAndStatus(
                tripId, NotificationStatus.PENDING));

        // 날짜를 하나씩 조회하지 않고 기준 도시를 한 번에 받아 둔다.
        Map<LocalDate, TravelPlace> cities = tripDayService.baseCitiesOf(tripId);
        for (int dayIndex = 0; dayIndex < trip.totalDays(); dayIndex++) {
            LocalDate date = trip.getStartDate().plusDays(dayIndex);
            ZoneId zone = zoneOn(cities, date);
            notificationRepository.saveAll(build(trip, activityRepository
                    .findAllByTripIdAndActivityDateOrderBySortOrderAscIdAsc(trip.getId(), date),
                    zone));

            if (trip.isMorningSummaryEnabled()) {
                notificationRepository.save(PushNotification.morningSummary(
                        trip.getMemberId(), trip.getId(), date, morningAt(date, zone)));
            }
        }
    }

    /**
     * 아침 요약 시각 — 그 날짜의 <b>현지</b> 08:00(§4.3).
     *
     * <p>그날 일정이 0건이어도 지금은 만든다. 판정은 <b>보내기 직전</b>에 한다 — 예약 때만 보면
     * 나중에 일정을 채운 날은 영영 요약이 안 오고, 다 지운 날엔 "일정 0개"가 간다.
     */
    private static Instant morningAt(LocalDate date, ZoneId zone) {
        return date.atTime(MORNING_SUMMARY_HOUR, 0).atZone(zone).toInstant();
    }

    /** 일정이 사라지거나 보관함으로 갔다 — 예약을 접는다. */
    @Transactional
    public void cancelForActivity(Long activityId) {
        cancelPending(activityId);
    }

    private void cancelPending(Long activityId) {
        cancelAll(notificationRepository.findAllByActivityIdAndStatus(
                activityId, NotificationStatus.PENDING));
    }

    private void cancelAll(List<PushNotification> notifications) {
        notifications.forEach(PushNotification::cancel);
        notificationRepository.saveAll(notifications);
    }

    /**
     * 그 날짜의 기준 도시 타임존. 날짜 행이 없으면(있을 수 없는 상태) 기기 타임존으로 버틴다 —
     * 여기서 터뜨리면 알림 재계산을 부르는 저장 요청이 통째로 실패한다.
     */
    private static ZoneId zoneOn(Map<LocalDate, TravelPlace> cities, LocalDate date) {
        TravelPlace city = cities.get(date);
        return city == null ? ZoneId.systemDefault() : TripDayService.zoneOf(city);
    }

    /** 그 날짜의 일정들에서 만들 알림을 전부 계산한다. 시각 환산은 그 날짜의 타임존으로 한다. */
    private List<PushNotification> build(Trip trip, List<TripActivity> ordered, ZoneId zone) {
        // 이동시간은 출발 알림에만 쓴다. 아무도 켜지 않았으면 조회할 이유가 없다 —
        // 일정을 저장할 때마다 유료 API를 부르게 되고, 저장이 그만큼 느려진다.
        Map<Long, TravelTimeResponse> travelTimesByTo = ordered.stream()
                .anyMatch(TripActivity::isDepartureNotifyEnabled)
                ? travelTimeService.travelTimes(ordered).stream()
                        .collect(Collectors.toMap(TravelTimeResponse::toActivityId, Function.identity()))
                : Map.of();

        List<PushNotification> created = new ArrayList<>();
        for (TripActivity activity : ordered) {
            // 시각이 없으면 어느 스위치가 켜져 있든 언제 보낼지 정할 수 없다(§1.2).
            if (activity.getActivityDate() == null || activity.getStartTime() == null) {
                continue;
            }
            Instant startAt = toInstant(activity, zone);

            // 두 스위치는 독립이다 — 출발 알림만 켜 두는 것도 말이 된다.
            if (activity.isNotifiable()) {
                created.add(PushNotification.forActivity(trip.getMemberId(), trip.getId(),
                        activity.getId(), NotificationType.ACTIVITY,
                        startAt.minusSeconds(60L
                                * activity.resolveNotifyMinutes(trip.getDefaultNotifyMinutes()))));
            }

            departureAt(activity, travelTimesByTo.get(activity.getId()), startAt)
                    .ifPresent(at -> created.add(PushNotification.forActivity(
                            trip.getMemberId(), trip.getId(), activity.getId(),
                            NotificationType.DEPARTURE, at)));
        }
        return created;
    }

    /**
     * 출발 알림 시각 = 시작시각 − 이동시간 − 5분.
     *
     * <p><b>직전 장소 있는 일정이 없으면 만들지 않는다.</b> 어디서 출발하는지 모르면 언제
     * 나서야 하는지도 모른다 — 화면에서도 그 경우 이 설정이 비활성이다.
     */
    private Optional<Instant> departureAt(TripActivity activity, TravelTimeResponse travelTime, Instant startAt) {
        if (!activity.isDepartureNotifyEnabled() || travelTime == null
                || travelTime.durationMinutes() == null) {
            // fallback(직선거리만 아는 구간)은 소요 시간을 모른다 — 지어내지 않는다.
            return Optional.empty();
        }
        return Optional.of(startAt.minusSeconds(60L
                * (travelTime.durationMinutes() + PushNotification.DEPARTURE_BUFFER_MINUTES)));
    }

    /**
     * 벽시계 시각 → UTC 절대시각.
     *
     * <p>이 환산이 알림의 전부다. 일정은 여행 타임존의 벽시계 값으로 저장되어 있고(§4.1),
     * 스케줄러는 UTC로 돈다. 여기서 어긋나면 알림이 몇 시간씩 밀린다.
     */
    private static Instant toInstant(TripActivity activity, ZoneId zone) {
        return LocalDateTime.of(activity.getActivityDate(), activity.getStartTime())
                .atZone(zone)
                .toInstant();
    }
}
