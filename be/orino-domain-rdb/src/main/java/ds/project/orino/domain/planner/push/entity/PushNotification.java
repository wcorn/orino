package ds.project.orino.domain.planner.push.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 예약된 알림 1건.
 *
 * <p><b>제목·본문을 저장하지 않는다.</b> 발송 시점에 일정을 다시 읽어 조립한다 — §4.2 재계산
 * 트리거에 "제목 변경"이 없어서, 저장해두면 제목만 고친 일정이 옛 제목으로 알림된다.
 *
 * <p>{@code scheduledAt}은 <b>UTC 절대시각</b>이다. 일정 시각은 여행 타임존의 벽시계 값이라
 * {@code trip.timezone}으로 환산해 넣는다. 타임존을 바꾸면 일정 시각은 그대로고 이 값만
 * 다시 계산된다(§4.1).
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "push_notification")
public class PushNotification {

    /** 출발 알림의 여유. 이동시간에 더해 이만큼 앞서 알린다(§4.2). */
    public static final int DEPARTURE_BUFFER_MINUTES = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    /** 아침 요약은 특정 일정에 매달리지 않는다 → null. */
    @Column(name = "activity_id")
    private Long activityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NotificationType type;

    /** 아침 요약이 가리키는 날짜. 그 외에는 null. */
    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "fail_reason", length = 255)
    private String failReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PushNotification() {
    }

    private PushNotification(Long memberId, Long tripId, Long activityId, NotificationType type,
                             LocalDate targetDate, Instant scheduledAt) {
        this.memberId = memberId;
        this.tripId = tripId;
        this.activityId = activityId;
        this.type = type;
        this.targetDate = targetDate;
        this.scheduledAt = scheduledAt;
    }

    public static PushNotification forActivity(Long memberId, Long tripId, Long activityId,
                                               NotificationType type, Instant scheduledAt) {
        return new PushNotification(memberId, tripId, activityId, type, null, scheduledAt);
    }

    public static PushNotification morningSummary(Long memberId, Long tripId,
                                                  LocalDate targetDate, Instant scheduledAt) {
        return new PushNotification(memberId, tripId, null,
                NotificationType.MORNING_SUMMARY, targetDate, scheduledAt);
    }

    /** 재계산·삭제로 더는 유효하지 않다. 지우지 않는 이유는 추적 가능성이다. */
    public void cancel() {
        this.status = NotificationStatus.CANCELED;
    }

    public void markSent(Instant sentAt) {
        this.status = NotificationStatus.SENT;
        this.sentAt = sentAt;
    }

    /** 이유는 컬럼 길이를 넘길 수 있다 — 넘기면 저장 자체가 실패한다. */
    public void markFailed(String reason, Instant attemptedAt) {
        this.status = NotificationStatus.FAILED;
        this.sentAt = attemptedAt;
        this.failReason = reason == null || reason.length() <= 255
                ? reason : reason.substring(0, 255);
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getTripId() {
        return tripId;
    }

    public Long getActivityId() {
        return activityId;
    }

    public NotificationType getType() {
        return type;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public String getFailReason() {
        return failReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
