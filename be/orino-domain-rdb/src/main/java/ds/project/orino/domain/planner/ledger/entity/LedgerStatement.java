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
 * 카드 청구서 한 장 — <b>이 모듈의 심장</b>(확정 명세 §7).
 *
 * <p><b>청구액을 저장하지 않는다.</b> 사용 합계·할부 회차·이월·수수료·환불·할인·차액을 그때그때
 * 더해서 낸다. 저장해 두면 사용 건 하나가 바뀔 때마다 누가 다시 계산해야 하고, 한 번 놓치면
 * 화면의 금액과 내역이 어긋난다 — 잔액을 컬럼으로 두지 않은 것과 같은 이유다(D-8).
 *
 * <p>여기 저장하는 것은 <b>계산으로 나오지 않는 값</b>뿐이다: 이월·수수료·차액·할인·납부액.
 * 전부 사람이 청구서를 보고 넣는 값이다.
 *
 * <p><b>이월 잔액은 지출이 아니다</b>(§7.5). 청구액에는 들어가지만 통계의 지출 합계에는 절대
 * 들어가지 않는다 — 이미 쓸 때 잡혔고, 갚는 행위는 지출이 아니다. 이월을 새 지출로 세면
 * 같은 돈을 두 번 센다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_statement")
public class LedgerStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "card_asset_id", nullable = false)
    private Long cardAssetId;

    @Column(name = "cycle_start", nullable = false)
    private LocalDate cycleStart;

    @Column(name = "cycle_end", nullable = false)
    private LocalDate cycleEnd;

    /** 영업일 보정을 마친 결제 예정일. */
    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerStatementStatus status;

    @Column(name = "carried_over_amount", nullable = false)
    private long carriedOverAmount;

    /** 리볼빙 수수료·연체 이자. <b>이것만</b> 「이자/수수료」 카테고리의 새 지출이 된다. */
    @Column(name = "interest_fee_amount", nullable = false)
    private long interestFeeAmount;

    @Column(name = "adjustment_amount", nullable = false)
    private long adjustmentAmount;

    /** 차액의 원인. 숫자만 남기면 다음 달에 그 금액이 무엇이었는지 알 수 없다. */
    @Column(name = "adjustment_category_id")
    private Long adjustmentCategoryId;

    @Column(name = "discount_amount", nullable = false)
    private long discountAmount;

    @Column(name = "paid_amount", nullable = false)
    private long paidAmount;

    /** <b>실제</b> 출금일. 청구서의 결제일과 다를 수 있다 — 다르면 실제가 맞다. */
    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Column(name = "payment_transaction_id")
    private Long paymentTransactionId;

    @Column(name = "carried_to_statement_id")
    private Long carriedToStatementId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerStatement() {
    }

    public LedgerStatement(Long memberId, Long cardAssetId, LocalDate cycleStart,
                           LocalDate cycleEnd, LocalDate paymentDate) {
        this.memberId = memberId;
        this.cardAssetId = cardAssetId;
        this.cycleStart = cycleStart;
        this.cycleEnd = cycleEnd;
        this.paymentDate = paymentDate;
        this.status = LedgerStatementStatus.COLLECTING;
    }

    /** 마감일이 지났다. 금액이 더 이상 늘지 않는다. */
    public void confirm() {
        if (status == LedgerStatementStatus.COLLECTING) {
            this.status = LedgerStatementStatus.CONFIRMED;
        }
    }

    /**
     * 납부를 기록한다.
     *
     * <p>전액이면 {@code PAID}, 모자라면 {@code PARTIAL}이다. 남은 잔액은 여기서 지우지 않는다 —
     * <b>부채로 계속 잡혀 있어야 하고</b>, 다음 청구서에 별도 항목으로 편입된다.
     */
    public void recordPayment(long amount, LocalDate paidOn, Long transactionId, long billed) {
        this.paidAmount += amount;
        this.paidOn = paidOn;
        this.paymentTransactionId = transactionId;
        this.status = this.paidAmount >= billed
                ? LedgerStatementStatus.PAID
                : LedgerStatementStatus.PARTIAL;
    }

    /** 이월을 받는다. 다음 사이클의 청구서에 <b>별도 항목</b>으로 얹힌다. */
    public void receiveCarryOver(long amount) {
        this.carriedOverAmount += amount;
    }

    public void markCarriedTo(Long statementId) {
        this.carriedToStatementId = statementId;
    }

    public void addInterestFee(long amount) {
        this.interestFeeAmount += amount;
    }

    public void adjust(long amount, Long categoryId) {
        this.adjustmentAmount += amount;
        this.adjustmentCategoryId = categoryId;
    }

    public void addDiscount(long amount) {
        this.discountAmount += amount;
    }

    /**
     * 결제일이 지났는데 아직 다 내지 않았는가. <b>저장하지 않고 물어볼 때마다 판정한다.</b>
     *
     * <p>미납은 <b>「무시」할 수 없다</b> — 눈에 거슬리는 게 목적이다(확정 명세 §6.4).
     * 확정하거나 건너뛰어야만 사라진다.
     */
    public boolean isOverdueOn(LocalDate today) {
        return status.isUnsettled() && paymentDate.isBefore(today);
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getCardAssetId() {
        return cardAssetId;
    }

    public LocalDate getCycleStart() {
        return cycleStart;
    }

    public LocalDate getCycleEnd() {
        return cycleEnd;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public LedgerStatementStatus getStatus() {
        return status;
    }

    public long getCarriedOverAmount() {
        return carriedOverAmount;
    }

    public long getInterestFeeAmount() {
        return interestFeeAmount;
    }

    public long getAdjustmentAmount() {
        return adjustmentAmount;
    }

    public Long getAdjustmentCategoryId() {
        return adjustmentCategoryId;
    }

    public long getDiscountAmount() {
        return discountAmount;
    }

    public long getPaidAmount() {
        return paidAmount;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public Long getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public Long getCarriedToStatementId() {
        return carriedToStatementId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
