package ds.project.orino.planner.travel.push.service;

import ds.project.orino.domain.planner.push.entity.NotificationStatus;
import ds.project.orino.domain.planner.push.entity.NotificationType;
import ds.project.orino.domain.planner.push.entity.PushNotification;
import ds.project.orino.domain.planner.push.entity.PushSubscription;
import ds.project.orino.domain.planner.push.repository.PushNotificationRepository;
import ds.project.orino.domain.planner.push.repository.PushSubscriptionRepository;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.planner.travel.repository.TripActivityRepository;
import ds.project.orino.planner.travel.day.service.TripDayService;
import ds.project.orino.planner.travel.push.send.WebPushSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 예약 시각이 지난 알림을 실제로 보낸다.
 *
 * <p><b>제목은 지금 읽는다.</b> 예약할 때 저장해두면 제목만 고친 일정이 옛 제목으로 알림된다 —
 * §4.2 재계산 트리거에 "제목 변경"이 없기 때문이다.
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final PushNotificationRepository notificationRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final TripActivityRepository activityRepository;
    private final TripDayService tripDayService;
    private final WebPushSender sender;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NotificationDispatchService(PushNotificationRepository notificationRepository,
                                       PushSubscriptionRepository subscriptionRepository,
                                       TripActivityRepository activityRepository,
                                       TripDayService tripDayService,
                                       WebPushSender sender,
                                       ObjectMapper objectMapper,
                                       Clock clock) {
        this.notificationRepository = notificationRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.activityRepository = activityRepository;
        this.tripDayService = tripDayService;
        this.sender = sender;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** 보낼 때가 된 것을 전부 처리한다. 하나가 실패해도 나머지는 계속 간다. */
    @Transactional
    public int dispatchDue() {
        Instant now = clock.instant();
        List<PushNotification> due = notificationRepository
                .findAllByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                        NotificationStatus.PENDING, now);

        int sent = 0;
        for (PushNotification notification : due) {
            if (dispatch(notification, now)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean dispatch(PushNotification notification, Instant now) {
        Optional<String> payload = payloadOf(notification);
        if (payload.isEmpty()) {
            // 일정이 사라졌는데 예약이 남은 경우. 보낼 내용이 없으니 접는다.
            notification.cancel();
            notificationRepository.save(notification);
            return false;
        }

        List<PushSubscription> subscriptions =
                subscriptionRepository.findAllByMemberId(notification.getMemberId());
        if (subscriptions.isEmpty()) {
            notification.markFailed("구독이 없습니다.", now);
            notificationRepository.save(notification);
            return false;
        }

        boolean anyDelivered = false;
        String lastReason = null;
        for (PushSubscription subscription : subscriptions) {
            WebPushSender.Result result = sender.send(subscription, payload.get());
            if (result.delivered()) {
                anyDelivered = true;
            } else {
                lastReason = result.reason();
                if (result.subscriptionGone()) {
                    // 죽은 주소에 계속 보내면 비용만 든다(§6).
                    subscriptionRepository.delete(subscription);
                }
            }
        }

        if (anyDelivered) {
            notification.markSent(now);
        } else {
            notification.markFailed(lastReason, now);
        }
        notificationRepository.save(notification);
        return anyDelivered;
    }

    /**
     * 발송 시점에 조립한다. 일정이 사라졌으면 빈 값이다.
     *
     * <p>탭했을 때 갈 곳({@code url})을 함께 싣는다 — SW가 이 값으로 창을 옮긴다.
     */
    private Optional<String> payloadOf(PushNotification notification) {
        if (notification.getType() == NotificationType.MORNING_SUMMARY) {
            return morningSummaryPayload(notification);
        }
        return activityRepository.findById(notification.getActivityId())
                .map(activity -> json(title(notification, activity), body(activity),
                        "/travel/activities/" + activity.getId(),
                        "activity-" + activity.getId()));
    }

    /**
     * 아침 요약(§4.3). <b>보내기 직전에 그날 일정을 다시 센다</b> — 예약 때만 판정하면
     * 나중에 채운 날은 요약이 영영 안 오고, 다 지운 날엔 "일정 0개"가 간다.
     *
     * <p>0건이면 빈 값이라 호출부가 예약을 접는다.
     */
    private Optional<String> morningSummaryPayload(PushNotification notification) {
        LocalDate date = notification.getTargetDate();
        List<TripActivity> ordered = activityRepository
                .findAllByTripIdAndActivityDateOrderBySortOrderAscIdAsc(
                        notification.getTripId(), date);
        if (ordered.isEmpty()) {
            return Optional.empty();
        }
        Map<LocalDate, TravelPlace> cities =
                tripDayService.baseCitiesOf(notification.getTripId());
        return Optional.of(json("오늘 일정", summaryBody(ordered, date, cities),
                boardPath(notification), "morning-" + date));
    }

    /**
     * {@code 교토 · 오늘 일정 4개 · 첫 일정 09:00 숙소 체크아웃} — 시각이 없으면 제목만 붙인다.
     *
     * <p><b>도시가 바뀌는 날은 {@code 오사카 → 교토 · 오늘 일정 3개}</b>(v2.1 §3.6). 그날 가장
     * 중요한 사실이 "어디로 옮긴다"라서 앞자리를 그것에 내주고, 첫 일정은 접는다 — 알림 본문은
     * 한 줄이고 도시 두 개가 이미 그 줄을 채운다.
     */
    private static String summaryBody(List<TripActivity> ordered, LocalDate date,
                                      Map<LocalDate, TravelPlace> cities) {
        String count = "오늘 일정 %d개".formatted(ordered.size());
        if (MorningCity.changesOn(date, cities)) {
            return "%s → %s · %s".formatted(cityName(cities.get(date.minusDays(1))),
                    cityName(cities.get(date)), count);
        }
        TripActivity first = ordered.get(0);
        String when = first.getStartTime() == null ? "" : first.getStartTime() + " ";
        String body = "%s · 첫 일정 %s%s".formatted(count, when, first.getTitle());
        TravelPlace today = cities.get(date);
        // 도시를 모르면 접두를 붙이지 않는다 — `도시 없음 · …`은 알려주는 게 아니라 거슬린다.
        return today == null ? body : "%s · %s".formatted(cityName(today), body);
    }

    private static String cityName(TravelPlace city) {
        if (city == null) {
            return "";
        }
        return city.getCityName() != null ? city.getCityName() : city.getName();
    }

    /** 특정 일정이 아니라 <b>그날 보드</b>로 보낸다 — 요약이 가리키는 것은 하루 전체다. */
    private static String boardPath(PushNotification notification) {
        return "/travel/trips/%d/board?date=%s"
                .formatted(notification.getTripId(), notification.getTargetDate());
    }

    private static String title(PushNotification notification, TripActivity activity) {
        return switch (notification.getType()) {
            case DEPARTURE -> "출발할 시간이에요";
            case MORNING_SUMMARY -> "오늘 일정";
            case ACTIVITY -> activity.getTitle();
        };
    }

    private static String body(TripActivity activity) {
        String time = activity.getStartTime() == null ? "" : activity.getStartTime() + " ";
        return time + activity.getTitle();
    }

    private String json(String title, String body, String url, String tag) {
        return objectMapper.writeValueAsString(
                Map.of("title", title, "body", body, "url", url, "tag", tag));
    }

    /** S-09 즉시 발송. 실기기 검증은 이것 없이 시작할 수 없다. */
    @Transactional(readOnly = true)
    public int sendTest(Long memberId) {
        List<PushSubscription> subscriptions = subscriptionRepository.findAllByMemberId(memberId);
        String payload = json("알림 테스트", "이 알림이 보이면 설정이 끝난 거예요.", "/travel", "test");

        int delivered = 0;
        for (PushSubscription subscription : subscriptions) {
            WebPushSender.Result result = sender.send(subscription, payload);
            if (result.delivered()) {
                delivered++;
            } else {
                log.warn("테스트 발송 실패: {}", result.reason());
            }
        }
        return delivered;
    }
}
