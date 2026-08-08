package ds.project.orino.planner.travel.push.service;

import ds.project.orino.domain.planner.push.entity.NotificationStatus;
import ds.project.orino.domain.planner.push.entity.NotificationType;
import ds.project.orino.domain.planner.push.entity.PushNotification;
import ds.project.orino.domain.planner.push.repository.PushNotificationRepository;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.planner.travel.repository.TripActivityRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.route.dto.LegResponse;
import ds.project.orino.planner.travel.route.service.LegService;
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

    private final PushNotificationRepository notificationRepository;
    private final TripActivityRepository activityRepository;
    private final TripRepository tripRepository;
    private final LegService legService;

    public NotificationScheduleService(PushNotificationRepository notificationRepository,
                                       TripActivityRepository activityRepository,
                                       TripRepository tripRepository,
                                       LegService legService) {
        this.notificationRepository = notificationRepository;
        this.activityRepository = activityRepository;
        this.tripRepository = tripRepository;
        this.legService = legService;
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
        notificationRepository.saveAll(build(trip, ordered));
    }

    /** 여행 전체를 다시 짠다 — 타임존이 바뀌면 모든 날짜의 환산 결과가 달라진다. */
    @Transactional
    public void rescheduleTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId).orElse(null);
        if (trip == null) {
            return;
        }
        cancelAll(notificationRepository.findAllByTripIdAndStatus(
                tripId, NotificationStatus.PENDING));

        for (int dayIndex = 0; dayIndex < trip.totalDays(); dayIndex++) {
            LocalDate date = trip.getStartDate().plusDays(dayIndex);
            notificationRepository.saveAll(build(trip, activityRepository
                    .findAllByTripIdAndActivityDateOrderBySortOrderAscIdAsc(trip.getId(), date)));
        }
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

    /** 그 날짜의 일정들에서 만들 알림을 전부 계산한다. */
    private List<PushNotification> build(Trip trip, List<TripActivity> ordered) {
        ZoneId zone = ZoneId.of(trip.getTimezone());
        // 이동시간은 출발 알림에만 쓴다. 아무도 켜지 않았으면 조회할 이유가 없다 —
        // 일정을 저장할 때마다 유료 API를 부르게 되고, 저장이 그만큼 느려진다.
        Map<Long, LegResponse> legsByTo = ordered.stream()
                .anyMatch(TripActivity::isDepartureNotifyEnabled)
                ? legService.legs(ordered).stream()
                        .collect(Collectors.toMap(LegResponse::toActivityId, Function.identity()))
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

            departureAt(activity, legsByTo.get(activity.getId()), startAt)
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
    private Optional<Instant> departureAt(TripActivity activity, LegResponse leg, Instant startAt) {
        if (!activity.isDepartureNotifyEnabled() || leg == null
                || leg.durationMinutes() == null) {
            // fallback(직선거리만 아는 구간)은 소요 시간을 모른다 — 지어내지 않는다.
            return Optional.empty();
        }
        return Optional.of(startAt.minusSeconds(60L
                * (leg.durationMinutes() + PushNotification.DEPARTURE_BUFFER_MINUTES)));
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
