package ds.project.orino.domain.planner.push.repository;

import ds.project.orino.domain.planner.push.entity.NotificationStatus;
import ds.project.orino.domain.planner.push.entity.NotificationType;
import ds.project.orino.domain.planner.push.entity.PushNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface PushNotificationRepository extends JpaRepository<PushNotification, Long> {

    /** 스케줄러 폴링. 30초마다 도는 쿼리라 {@code idx_notification_due}가 받쳐 준다. */
    List<PushNotification> findAllByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            NotificationStatus status, Instant now);

    /** 재계산 시 그 일정의 대기 중 알림을 전부 접는다. */
    List<PushNotification> findAllByActivityIdAndStatus(Long activityId, NotificationStatus status);

    /** 여행 단위 재계산(타임존 변경 등). */
    List<PushNotification> findAllByTripIdAndStatus(Long tripId, NotificationStatus status);

    /**
     * 날짜에 걸린 알림(아침 요약)의 재계산.
     *
     * <p>일정 id로는 찾을 수 없다 — 아침 요약은 하루 전체를 가리키므로 특정 일정에 붙어 있지
     * 않다. 기준 도시가 바뀌면 이 경로로 찾아 다시 잡는다.
     */
    List<PushNotification> findAllByTripIdAndTypeAndTargetDateInAndStatus(
            Long tripId, NotificationType type, Collection<LocalDate> targetDates,
            NotificationStatus status);

    /**
     * 종류로 찾는 재계산. 준비 알림은 <b>여행당 하나</b>라 날짜로 좁힐 것이 없다 —
     * 게다가 그 날짜는 여행 기간 밖(출발 전날)이라 날짜 집합으로 거는 경로에 얹히지 않는다.
     */
    List<PushNotification> findAllByTripIdAndTypeAndStatus(
            Long tripId, NotificationType type, NotificationStatus status);

    List<PushNotification> findAllByMemberIdOrderByScheduledAtAsc(Long memberId);
}
