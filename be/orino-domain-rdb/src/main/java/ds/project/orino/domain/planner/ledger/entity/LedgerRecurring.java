package ds.project.orino.domain.planner.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 정기 항목 — 구독·보험·자동이체·고정비를 <b>하나의 개념</b>으로 담는 한 행.
 *
 * <p><b>회차는 여기 없다.</b> 회차는 이 규칙에서 파생 계산하고, 사람이 손댄 회차만
 * {@link LedgerRecurringOverride} 1행을 남긴다(D-5). 전부 실체화하면 규칙을 고칠 때마다
 * 12개월치를 UPDATE해야 하고, 전부 파생이면 「이번 달만 17,000원」을 저장할 곳이 없다.
 *
 * <p><b>규칙을 고치면 앞으로의 예정만 바뀐다</b>(확정 명세 §6.5). 파생 계산이라 자동으로
 * 그렇게 된다 — "이 건만 / 이후 모두 / 전체"를 묻지 않고, 이미 원장에 들어간 과거 내역은
 * 건드리지 않는다. 지난달에 12,000원 낸 사실은 그대로 남는다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_recurring")
public class LedgerRecurring {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerRecurringKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type", nullable = false, length = 20)
    private LedgerFlow txType;

    /** 고정이면 실제 금액, 변동이면 예상액. 바뀔 때마다 이력 한 줄이 남는다. */
    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "amount_type", nullable = false, length = 20)
    private LedgerAmountType amountType;

    /** 결제 수단. 카드면 적히는 순간 그 카드 청구서에 편입된다. */
    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "counter_asset_id")
    private Long counterAssetId;

    @Column(name = "category_id")
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "freq_type", nullable = false, length = 30)
    private LedgerFrequencyType freqType;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "freq_interval")
    private Integer freqInterval;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "freq_day")
    private Integer freqDay;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "freq_month")
    private Integer freqMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_day_policy", nullable = false, length = 20)
    private LedgerBusinessDayPolicy businessDayPolicy;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** NULL = 무기한. 점검 신호 ④가 이 NULL을 센다. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "paused_from")
    private LocalDate pausedFrom;

    @Column(name = "paused_to")
    private LocalDate pausedTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerRecurringStatus status;

    /** 해지일. 소급 해지가 가능하므로 오늘보다 과거일 수 있다. */
    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Column(name = "cancel_url", length = 500)
    private String cancelUrl;

    @Column(length = 500)
    private String memo;

    /**
     * 자동 기록의 하한. 등록 시점에 {@code max(startDate, 오늘)}로 박힌다.
     *
     * <p>과거 시작일로 새 항목을 만들었다고 지난 여섯 달치가 쏟아지면 원장이 오염된다 —
     * 시작일은 「언제부터 쓰던 구독인가」이고, 이 값은 「언제부터 우리가 적는가」다.
     */
    @Column(name = "posting_from", nullable = false)
    private LocalDate postingFrom;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerRecurring() {
    }

    public LedgerRecurring(Long memberId,
                           String name,
                           LedgerRecurringKind kind,
                           LedgerFlow txType,
                           long amount,
                           LedgerAmountType amountType,
                           Long assetId,
                           LedgerFrequencyType freqType,
                           LocalDate startDate,
                           LocalDate postingFrom) {
        this.postingFrom = postingFrom;
        this.memberId = memberId;
        this.name = name;
        this.kind = kind;
        this.txType = txType;
        this.amount = amount;
        this.amountType = amountType;
        this.assetId = assetId;
        this.freqType = freqType;
        this.startDate = startDate;
        this.businessDayPolicy = LedgerBusinessDayPolicy.AS_IS;
        this.status = LedgerRecurringStatus.ACTIVE;
    }

    public void updateRule(LedgerFrequencyType freqType, Integer freqInterval,
                           Integer freqDay, Integer freqMonth) {
        this.freqType = freqType;
        this.freqInterval = freqInterval;
        this.freqDay = freqDay;
        this.freqMonth = freqMonth;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateKind(LedgerRecurringKind kind) {
        this.kind = kind;
    }

    public void updateAmount(long amount) {
        this.amount = amount;
    }

    public void updateAmountType(LedgerAmountType amountType) {
        this.amountType = amountType;
    }

    public void updateTarget(Long assetId, Long counterAssetId, Long categoryId) {
        this.assetId = assetId;
        this.counterAssetId = counterAssetId;
        this.categoryId = categoryId;
    }

    public void updateBusinessDayPolicy(LedgerBusinessDayPolicy businessDayPolicy) {
        this.businessDayPolicy = businessDayPolicy;
    }

    public void updatePeriod(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void updateMemo(String cancelUrl, String memo) {
        this.cancelUrl = cancelUrl;
        this.memo = memo;
    }

    /** 기간을 정해 쉰다. 규칙은 그대로 두고 그 구간의 회차만 전개에서 빠진다. */
    public void pause(LocalDate from, LocalDate to) {
        this.pausedFrom = from;
        this.pausedTo = to;
        this.status = LedgerRecurringStatus.PAUSED;
    }

    public void resume() {
        this.pausedFrom = null;
        this.pausedTo = null;
        this.status = LedgerRecurringStatus.ACTIVE;
    }

    /** 해지. 행을 지우지 않는다 — 연간 고정비 회고에 「올해 넉 달 냈다」가 남아야 한다. */
    public void end(LocalDate endedOn) {
        this.endedOn = endedOn;
        this.status = LedgerRecurringStatus.ENDED;
    }

    /**
     * 그날 회차가 살아 있는가. 정지 구간과 해지일과 종료일을 함께 본다.
     *
     * <p>정지는 <b>구간</b>이라 {@code status}만으로는 판정할 수 없다 — 지난달 회차를
     * 지금 전개할 때 「그때는 쉬고 있었나」를 물어야 하기 때문이다.
     */
    public boolean isActiveOn(LocalDate date) {
        if (date.isBefore(startDate)) {
            return false;
        }
        if (endDate != null && date.isAfter(endDate)) {
            return false;
        }
        if (endedOn != null && !date.isBefore(endedOn)) {
            return false;
        }
        return !isPausedOn(date);
    }

    public boolean isPausedOn(LocalDate date) {
        if (pausedFrom == null) {
            return false;
        }
        if (date.isBefore(pausedFrom)) {
            return false;
        }
        return pausedTo == null || !date.isAfter(pausedTo);
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getName() {
        return name;
    }

    public LedgerRecurringKind getKind() {
        return kind;
    }

    public LedgerFlow getTxType() {
        return txType;
    }

    public long getAmount() {
        return amount;
    }

    public LedgerAmountType getAmountType() {
        return amountType;
    }

    public Long getAssetId() {
        return assetId;
    }

    public Long getCounterAssetId() {
        return counterAssetId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public LedgerFrequencyType getFreqType() {
        return freqType;
    }

    public Integer getFreqInterval() {
        return freqInterval;
    }

    public Integer getFreqDay() {
        return freqDay;
    }

    public Integer getFreqMonth() {
        return freqMonth;
    }

    public LedgerBusinessDayPolicy getBusinessDayPolicy() {
        return businessDayPolicy;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getPostingFrom() {
        return postingFrom;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalDate getPausedFrom() {
        return pausedFrom;
    }

    public LocalDate getPausedTo() {
        return pausedTo;
    }

    public LedgerRecurringStatus getStatus() {
        return status;
    }

    public LocalDate getEndedOn() {
        return endedOn;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public String getMemo() {
        return memo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
