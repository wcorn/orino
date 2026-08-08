package ds.project.orino.domain.planner.push.repository;

import ds.project.orino.domain.planner.push.entity.NotificationStatus;
import ds.project.orino.domain.planner.push.entity.PushNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PushNotificationRepository extends JpaRepository<PushNotification, Long> {

    /** 스케줄러 폴링. 30초마다 도는 쿼리라 {@code idx_notification_due}가 받쳐 준다. */
    List<PushNotification> findAllByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            NotificationStatus status, Instant now);

    /** 재계산 시 그 일정의 대기 중 알림을 전부 접는다. */
    List<PushNotification> findAllByActivityIdAndStatus(Long activityId, NotificationStatus status);

    /** 여행 단위 재계산(타임존 변경 등). */
    List<PushNotification> findAllByTripIdAndStatus(Long tripId, NotificationStatus status);

    List<PushNotification> findAllByMemberIdOrderByScheduledAtAsc(Long memberId);
}
