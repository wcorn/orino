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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 여행 1건. 일정({@link TripActivity})의 소유자이자 타임존·통화의 기준점이다.
 *
 * <p><b>상태·D-day·일차 번호를 저장하지 않는다.</b> 셋 다 "오늘"에 의존하는 값이라 컬럼으로 두면
 * 날짜가 넘어가는 순간 어긋난다. 대신 {@link #status(Clock)}·{@link #daysUntilStart(Clock)}으로
 * 조회 시마다 파생하며, 기준은 기기 시간대가 아니라 {@link #timezone}의 오늘이다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "trip")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 최대 50자. 미입력 시 목적지명으로 채워 저장한다(빈 제목을 만들지 않는다). */
    @Column(nullable = false, length = 50)
    private String title;

    /** 목적지 표시명. 목록 카드 메타에 쓰려고 denormalize한다. */
    @Column(name = "destination_name", nullable = false, length = 100)
    private String destinationName;

    /** 검색으로 고른 목적지 도시({@link TravelPlace}). 1단계는 항상 null(수동 입력). */
    @Column(name = "destination_place_id")
    private Long destinationPlaceId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 종료일(당일 포함). 항상 {@code >= startDate} — 애플리케이션에서 검증한다. */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** IANA 타임존 ID(예: {@code Asia/Tokyo}). 상태·D-day·알림 시각 환산의 유일한 기준. */
    @Column(nullable = false, length = 64)
    private String timezone;

    /** ISO 4217 통화 코드(예: {@code JPY}). */
    @Column(nullable = false, length = 3)
    private String currency;

    /** 목적지 좌표 — 날씨 조회 기준점. */
    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    /** 여행 단위 기본 알림 시점(분 전). 일정이 값을 따로 정하지 않으면 이걸 쓴다. */
    @Column(name = "default_notify_minutes", nullable = false)
    private int defaultNotifyMinutes = 15;

    @Column(name = "morning_summary_enabled", nullable = false)
    private boolean morningSummaryEnabled = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Trip() {
    }

    public Trip(Long memberId, String title, String destinationName, LocalDate startDate,
                LocalDate endDate, String timezone, String currency) {
        this.memberId = memberId;
        this.title = title;
        this.destinationName = destinationName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.timezone = timezone;
        this.currency = currency;
    }

    /** 제목·목적지·기간·타임존·통화 등 기본 정보를 갱신한다. */
    public void update(String title, String destinationName, LocalDate startDate,
                       LocalDate endDate, String timezone, String currency) {
        this.title = title;
        this.destinationName = destinationName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.timezone = timezone;
        this.currency = currency;
    }

    /** 목적지 좌표(날씨 기준점)와 검색으로 고른 목적지 장소를 잇는다. 2단계부터 쓴다. */
    public void updateDestinationPlace(Long destinationPlaceId, BigDecimal lat, BigDecimal lng) {
        this.destinationPlaceId = destinationPlaceId;
        this.lat = lat;
        this.lng = lng;
    }

    public void updateNotificationSettings(int defaultNotifyMinutes, boolean morningSummaryEnabled) {
        this.defaultNotifyMinutes = defaultNotifyMinutes;
        this.morningSummaryEnabled = morningSummaryEnabled;
    }

    /** 여행 타임존 기준 오늘 날짜. 상태·D-day·일차 계산은 전부 이 값을 기준으로 한다. */
    public LocalDate todayAtDestination(Clock clock) {
        return LocalDate.now(clock.withZone(ZoneId.of(timezone)));
    }

    /** 여행 타임존의 오늘로 판정한 상태. */
    public TripStatus status(Clock clock) {
        return statusOn(todayAtDestination(clock));
    }

    /** 주어진 날짜를 오늘로 보고 판정한 상태. 목록을 한 번에 훑을 때 오늘을 재사용한다. */
    public TripStatus statusOn(LocalDate today) {
        return TripStatus.of(today, startDate, endDate);
    }

    /**
     * 시작일까지 남은 일수(D-day). 시작 당일이면 0, 이미 시작했으면 음수다.
     * 표시 여부(예정 여행에만 노출)는 호출부가 {@link #status(Clock)}로 판단한다.
     */
    public long daysUntilStart(Clock clock) {
        return ChronoUnit.DAYS.between(todayAtDestination(clock), startDate);
    }

    /** 여행 총 일수(당일 포함). 하루짜리 여행이면 1. */
    public int totalDays() {
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    /** 해당 날짜의 일차 번호(시작일이 1일차). 여행 기간 밖이면 범위를 벗어난 값이 나온다. */
    public int dayNumberOf(LocalDate date) {
        return (int) ChronoUnit.DAYS.between(startDate, date) + 1;
    }

    /** 여행 기간에 속한 날짜인지. 일정의 {@code activityDate} 검증에 쓴다. */
    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getTitle() {
        return title;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public Long getDestinationPlaceId() {
        return destinationPlaceId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public int getDefaultNotifyMinutes() {
        return defaultNotifyMinutes;
    }

    public boolean isMorningSummaryEnabled() {
        return morningSummaryEnabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
