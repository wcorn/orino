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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 여행 1건. 일정({@link TripActivity})의 소유자이자 기간의 주인이다.
 *
 * <p><b>v2.1 — 여행은 도시·타임존·통화를 갖지 않는다.</b> 목적지는 날짜가 갖는다
 * ({@link TripDay#getBasePlaceId()}). 여행 하나에 타임존 하나라는 가정이 한 군데라도 남으면
 * 오사카 → 교토 → 나고야를 옮겨 다닐 때 그 화면만 조용히 틀리기 때문에, 컬럼을 NULL 허용으로
 * 남기지 않고 지웠다.
 *
 * <p><b>상태·D-day·일차 번호를 저장하지 않는다.</b> 셋 다 "오늘"에 의존하는 값이라 컬럼으로 두면
 * 날짜가 넘어가는 순간 어긋난다. 대신 {@link #status(Clock, ZoneId)}·
 * {@link #daysUntilStart(Clock, ZoneId)}으로 조회 시마다 파생한다. 기준 타임존은 기기 시간대도
 * 여행의 것도 아니라 <b>날짜의 기준 도시</b>에서 오므로, 호출부가 어느 날짜의 타임존인지 정해
 * 넘긴다(어느 날짜를 쓰는지는 값마다 다르다 — #1123).
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

    /** 최대 50자. v2.1부터 필수다 — 목적지가 여행에 없으니 자동으로 채울 이름도 없다. */
    @Column(nullable = false, length = 50)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 종료일(당일 포함). 항상 {@code >= startDate} — 애플리케이션에서 검증한다. */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 여행 단위 기본 알림 시점(분 전). 일정이 값을 따로 정하지 않으면 이걸 쓴다. */
    @Column(name = "default_notify_minutes", nullable = false)
    private int defaultNotifyMinutes = 15;

    @Column(name = "morning_summary_enabled", nullable = false)
    private boolean morningSummaryEnabled = false;

    /**
     * 여행 예산(원화). 아직 안 정했으면 null이다 — 0원과 「안 정했다」는 다르다.
     *
     * <p>가계부의 {@code ledger_budget}에 넣지 않는다(D-28). 월 예산은 달에 걸리지만 여행
     * 예산은 여행에 걸리고, 그 둘은 서로 넘나든다 — 10월 예산에 여행 예산을 접어 넣으면
     * "이 달 여행으로 얼마"를 다시 빼낼 방법이 없다.
     */
    @Column(name = "budget_amount")
    private Long budgetAmount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Trip() {
    }

    public Trip(Long memberId, String title, LocalDate startDate, LocalDate endDate) {
        this.memberId = memberId;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** 제목·기간을 갱신한다. 목적지는 여행이 아니라 날짜가 갖는다. */
    public void update(String title, LocalDate startDate, LocalDate endDate) {
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void updateNotificationSettings(int defaultNotifyMinutes, boolean morningSummaryEnabled) {
        this.defaultNotifyMinutes = defaultNotifyMinutes;
        this.morningSummaryEnabled = morningSummaryEnabled;
    }

    /**
     * 주어진 타임존 기준 오늘 날짜. 상태·D-day·일차 계산이 전부 이 값을 쓴다.
     *
     * <p>타임존을 인자로 받는 이유 — 여행에는 타임존이 없다. 어느 날짜의 기준 도시를 쓸지는
     * 값마다 달라서(상태는 오늘 날짜, D-day는 첫날) 호출부가 정한다.
     */
    public LocalDate todayIn(Clock clock, ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
    }

    /** 주어진 타임존의 오늘로 판정한 상태. */
    public TripStatus status(Clock clock, ZoneId zone) {
        return statusOn(todayIn(clock, zone));
    }

    /** 주어진 날짜를 오늘로 보고 판정한 상태. 목록을 한 번에 훑을 때 오늘을 재사용한다. */
    public TripStatus statusOn(LocalDate today) {
        return TripStatus.of(today, startDate, endDate);
    }

    /**
     * 시작일까지 남은 일수(D-day). 시작 당일이면 0, 이미 시작했으면 음수다.
     * 표시 여부(예정 여행에만 노출)는 호출부가 {@link #status(Clock, ZoneId)}로 판단한다.
     */
    public long daysUntilStart(Clock clock, ZoneId zone) {
        return ChronoUnit.DAYS.between(todayIn(clock, zone), startDate);
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

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getDefaultNotifyMinutes() {
        return defaultNotifyMinutes;
    }

    public boolean isMorningSummaryEnabled() {
        return morningSummaryEnabled;
    }

    /** 예산을 정하거나 해제한다. {@code null}이면 「안 정함」이다 — 0과 다르다. */
    public void updateBudgetAmount(Long budgetAmount) {
        this.budgetAmount = budgetAmount;
    }

    public Long getBudgetAmount() {
        return budgetAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
