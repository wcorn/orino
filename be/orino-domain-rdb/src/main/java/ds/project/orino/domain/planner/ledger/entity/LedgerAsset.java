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
 * 자산 1건. <b>모든 거래는 자산에 붙는다</b> — 이 제약 하나가 카드별·은행별 뷰와 잔액 정합성을
 * 전부 만든다(확정 명세 §3-1).
 *
 * <p><b>{@code balance} 컬럼이 없다</b>(D-8). 잔액은 원장에서 파생한다 — 저장한 잔액과 원장이
 * 어긋나는 순간이 수동 가계부가 신뢰를 잃는 전형적 경로다. 어긋남을 감추는 컬럼보다
 * 어긋남이 드러나는 합계 질의가 낫다.
 *
 * <p>자산은 <b>지우지 않고 숨긴다</b>. 해지한 카드의 지난 3년 내역이 갈 곳을 잃으면
 * 그것도 원장이 틀어지는 길이다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_asset")
public class LedgerAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** NULL이면 화면의 「그 외」 묶음. 그룹은 표시 수단이지 소속 조건이 아니다. */
    @Column(name = "group_id")
    private Long groupId;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerAssetType type;

    @Column(name = "account_last4", length = 4)
    private String accountLast4;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "closed_reason", length = 30)
    private String closedReason;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "target_amount")
    private Long targetAmount;

    /**
     * 체크카드의 연결 계좌. <b>체크카드에는 반드시 있어야 한다</b>(LDG-ERR-019) —
     * 연결이 없으면 잔액이 어디서도 빠지지 않는 유령 자산이 된다.
     */
    @Column(name = "linked_asset_id")
    private Long linkedAssetId;

    /**
     * 대금이 빠져나갈 계좌. 결제 처리의 <b>기본값</b>이지 강제는 아니다 —
     * 그날 다른 통장에서 냈다면 그쪽으로 적어야 원장이 맞는다.
     */
    @Column(name = "payment_asset_id")
    private Long paymentAssetId;

    /** 정산 시작일·마감일·결제일(1~28 또는 99=말일). <b>셋이 함께 있어야</b> 사이클이 성립한다. */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "cycle_start_day")
    private Integer cycleStartDay;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "cycle_close_day")
    private Integer cycleCloseDay;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "payment_day")
    private Integer paymentDay;

    @Column(name = "credit_limit")
    private Long creditLimit;

    /** 월 실적 조건 금액. NULL이면 이 카드에 실적을 걸지 않았다는 뜻이다(v2). */
    @Column(name = "usage_goal_amount")
    private Long usageGoalAmount;

    /**
     * 실적을 <b>무엇으로 세는가</b>. 승인이냐 청구냐는 카드사·상품마다 달라
     * 카드마다 따로 갖는다 — 전역 설정으로 두면 카드 두 장에서 한쪽이 반드시 틀린다(§7.6).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "usage_goal_basis", length = 20)
    private LedgerUsageGoalBasis usageGoalBasis;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerAsset() {
    }

    public LedgerAsset(Long memberId, String name, LedgerAssetType type) {
        this.memberId = memberId;
        this.name = name;
        this.type = type;
        this.displayOrder = 0;
        this.hidden = false;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public void updateAccountLast4(String accountLast4) {
        this.accountLast4 = accountLast4;
    }

    public void updateDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    /** 숨김을 풀면 배지 문구도 함께 사라진다 — 살아 있는 자산에 「해지」가 남으면 거짓이다. */
    public void updateHidden(boolean hidden, String closedReason) {
        this.hidden = hidden;
        this.closedReason = hidden ? closedReason : null;
    }

    public void updateMaturityDate(LocalDate maturityDate) {
        this.maturityDate = maturityDate;
    }

    public void updateTargetAmount(Long targetAmount) {
        this.targetAmount = targetAmount;
    }

    public void updateLinkedAssetId(Long linkedAssetId) {
        this.linkedAssetId = linkedAssetId;
    }

    /**
     * 잔액이 실제로 빠지는 자산. 체크카드면 연결 계좌, 그 밖에는 자기 자신이다(D-4).
     *
     * <p>거래의 {@code assetId}는 언제나 체크카드 그대로 둔다 — 그래야 "이 카드로 얼마 썼나"에
     * 답할 수 있다. 전가는 <b>잔액 계산에서만</b> 일어난다.
     */
    public Long balanceBearingAssetId() {
        if (type == LedgerAssetType.DEBIT_CARD && linkedAssetId != null) {
            return linkedAssetId;
        }
        return id;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public String getName() {
        return name;
    }

    public LedgerAssetType getType() {
        return type;
    }

    public String getAccountLast4() {
        return accountLast4;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isHidden() {
        return hidden;
    }

    public String getClosedReason() {
        return closedReason;
    }

    public LocalDate getMaturityDate() {
        return maturityDate;
    }

    public Long getTargetAmount() {
        return targetAmount;
    }

    public Long getLinkedAssetId() {
        return linkedAssetId;
    }

    /** 사이클 설정이 다 갖춰졌는지. 하나라도 비면 청구서를 만들 수 없다. */
    public boolean hasBillingCycle() {
        return type == LedgerAssetType.CREDIT_CARD
                && cycleStartDay != null && cycleCloseDay != null && paymentDay != null;
    }

    public void updateBillingCycle(Integer cycleStartDay, Integer cycleCloseDay,
                                   Integer paymentDay, Long paymentAssetId, Long creditLimit) {
        if (cycleStartDay != null) {
            this.cycleStartDay = cycleStartDay;
        }
        if (cycleCloseDay != null) {
            this.cycleCloseDay = cycleCloseDay;
        }
        if (paymentDay != null) {
            this.paymentDay = paymentDay;
        }
        if (paymentAssetId != null) {
            this.paymentAssetId = paymentAssetId;
        }
        if (creditLimit != null) {
            this.creditLimit = creditLimit;
        }
    }

    public Long getPaymentAssetId() {
        return paymentAssetId;
    }

    public Integer getCycleStartDay() {
        return cycleStartDay;
    }

    public Integer getCycleCloseDay() {
        return cycleCloseDay;
    }

    public Integer getPaymentDay() {
        return paymentDay;
    }

    /** 실적 조건. 조건 금액과 기준은 함께 있거나 함께 없다 — 하나만 있으면 셀 수가 없다. */
    public void updateUsageGoal(Long usageGoalAmount, LedgerUsageGoalBasis usageGoalBasis) {
        this.usageGoalAmount = usageGoalAmount;
        this.usageGoalBasis = usageGoalBasis;
    }

    public Long getUsageGoalAmount() {
        return usageGoalAmount;
    }

    public LedgerUsageGoalBasis getUsageGoalBasis() {
        return usageGoalBasis;
    }

    public boolean hasUsageGoal() {
        return usageGoalAmount != null && usageGoalAmount > 0 && usageGoalBasis != null;
    }

    public Long getCreditLimit() {
        return creditLimit;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
