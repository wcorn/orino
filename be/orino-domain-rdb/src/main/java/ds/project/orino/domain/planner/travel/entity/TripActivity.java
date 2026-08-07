package ds.project.orino.domain.planner.travel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 일정 1건. {@code activityDate}가 null이면 <b>미배정 보관함</b>이다(날짜를 아직 못 정한 후보).
 *
 * <p><b>시각은 여행 타임존의 벽시계 값이다.</b> {@code activityDate}는 {@code DATE},
 * {@code startTime}은 {@code TIME}으로 담고 UTC로 환산하지 않는다. {@code Instant}로 저장하면
 * 여행 타임존을 바꾸는 순간 전 일정이 통째로 밀린다. 절대시각이 필요한 곳은 알림 예약뿐이고,
 * 거기서만 {@code trip.timezone}으로 환산한다.
 *
 * <p><b>정렬은 {@code sortOrder}만 본다.</b> 시각으로 정렬하지 않는다 — 시각 없는 일정이
 * 허용되고, 사용자가 드래그로 정한 순서가 시각보다 우선한다. 순서 변경은 해당 날짜의 행 전체를
 * 0..n-1로 재부여한다(하루 10건 남짓이라 gap index는 과설계다).
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "trip_activity")
public class TripActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(nullable = false, length = 100)
    private String title;

    /** null = 미배정 보관함. 값이 있으면 여행 기간 내 날짜여야 한다({@link Trip#covers}). */
    @Column(name = "activity_date")
    private LocalDate activityDate;

    /** 같은 날짜(또는 보관함) 안에서의 순서. 0부터 빈틈없이 부여한다. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 여행 타임존의 벽시계 시각. null이면 시각 미정 일정이다. */
    @Column(name = "start_time")
    private LocalTime startTime;

    /** 연결된 {@link TravelPlace}. 2단계(장소 검색)부터 채운다. */
    @Column(name = "place_id")
    private Long placeId;

    @Column(length = 1000)
    private String memo;

    /** 예약 페이지 링크 1개. */
    @Column(length = 500)
    private String url;

    @Column(name = "notify_enabled", nullable = false)
    private boolean notifyEnabled = false;

    /** 알림 시점(분 전). null이면 {@link Trip#getDefaultNotifyMinutes()}를 쓴다. */
    @Column(name = "notify_minutes")
    private Integer notifyMinutes;

    @Column(name = "departure_notify_enabled", nullable = false)
    private boolean departureNotifyEnabled = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TripActivity() {
    }

    public TripActivity(Long tripId, String title, LocalDate activityDate, int sortOrder,
                        LocalTime startTime) {
        this.tripId = tripId;
        this.title = title;
        this.activityDate = activityDate;
        this.sortOrder = sortOrder;
        this.startTime = startTime;
    }

    /** 제목·시각·메모·링크 갱신. 날짜와 순서는 이동 전용 메서드로만 바꾼다. */
    public void update(String title, LocalTime startTime, String memo, String url) {
        this.title = title;
        this.startTime = startTime;
        this.memo = memo;
        this.url = url;
    }

    /**
     * 날짜와 순서를 함께 옮긴다. 날짜 이동이면 양쪽 날짜를 재인덱싱해야 하므로
     * 호출부가 한 트랜잭션 안에서 처리한다.
     *
     * @param activityDate null이면 보관함으로 내린다
     */
    public void moveTo(LocalDate activityDate, int sortOrder) {
        this.activityDate = activityDate;
        this.sortOrder = sortOrder;
    }

    /** 같은 날짜 안에서 순서만 재부여한다(0..n-1 재인덱싱). */
    public void reorderTo(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void updatePlace(Long placeId) {
        this.placeId = placeId;
    }

    public void updateNotification(boolean notifyEnabled, Integer notifyMinutes,
                                   boolean departureNotifyEnabled) {
        this.notifyEnabled = notifyEnabled;
        this.notifyMinutes = notifyMinutes;
        this.departureNotifyEnabled = departureNotifyEnabled;
    }

    /** 날짜가 없는 미배정 일정(보관함)인지. */
    public boolean isUnscheduled() {
        return activityDate == null;
    }

    /**
     * 알림을 실제로 예약할 수 있는 일정인지. <b>DB 제약이 아니라 여기서 판정한다</b> —
     * 시각이 없으면 알림 스위치가 켜져 있어도 언제 보낼지 정할 수 없고, 보관함 일정은
     * 날짜조차 없다.
     */
    public boolean isNotifiable() {
        return notifyEnabled && activityDate != null && startTime != null;
    }

    /** 이 일정에 적용할 알림 시점(분 전). 자체 값이 없으면 여행 기본값으로 떨어진다. */
    public int resolveNotifyMinutes(int tripDefaultMinutes) {
        return notifyMinutes != null ? notifyMinutes : tripDefaultMinutes;
    }

    public Long getId() {
        return id;
    }

    public Long getTripId() {
        return tripId;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public Long getPlaceId() {
        return placeId;
    }

    public String getMemo() {
        return memo;
    }

    public String getUrl() {
        return url;
    }

    public boolean isNotifyEnabled() {
        return notifyEnabled;
    }

    public Integer getNotifyMinutes() {
        return notifyMinutes;
    }

    public boolean isDepartureNotifyEnabled() {
        return departureNotifyEnabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
