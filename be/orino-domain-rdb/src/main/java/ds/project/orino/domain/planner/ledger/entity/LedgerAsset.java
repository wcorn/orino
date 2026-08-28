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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
