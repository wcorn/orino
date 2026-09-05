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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 원장 한 줄. 이 모듈의 SSOT다.
 *
 * <p>지켜지는 것 셋.
 * <ol>
 *   <li>{@code assetId}는 <b>NOT NULL</b>이다. 자산 없는 거래를 허용하면 카드별·은행별 뷰와
 *       잔액이 전부 무너진다(확정 명세 §3-1)</li>
 *   <li>{@code amount}는 <b>항상 양수</b>다. 부호는 {@link LedgerFlow}가 정한다 — 음수를
 *       허용하면 합계 질의마다 부호 규칙이 갈리고, 그중 하나만 틀려도 조용히 어긋난다</li>
 *   <li>{@code deletedAt}이 채워질 뿐 <b>행은 지우지 않는다</b>. 되돌릴 수 있어야 하고,
 *       환불·취소는 삭제가 아니라 {@code refundOfId}로 연결된 반대 거래다(확정 명세 §4.3)</li>
 * </ol>
 *
 * <p>외화는 <b>근거</b>다. 원장에 남는 값은 언제나 원화 환산액({@code amount})이고,
 * {@code fx*} 셋은 그 값이 어떻게 나왔는지를 설명할 뿐이다. 환율은 거래 시점에 고정하고
 * 조회 시점으로 재계산하지 않는다 — 재계산하면 과거 지출액이 매일 바뀌어
 * 「지난달 얼마 썼나」에 답할 수 없다(D-9).
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_transaction")
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerFlow type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerTransactionStatus status;

    /** 소비·발생 일자. 통계와 타임라인의 기준은 청구일이 아니라 이 날짜다. */
    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    /** 시각까지 적었을 때만 채운다. 대부분의 입력은 날짜까지다. */
    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    @Column(nullable = false)
    private long amount;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    /** 이체 대상. {@code type == TRANSFER}면 반드시 있고, 그 밖에는 반드시 없다. */
    @Column(name = "counter_asset_id")
    private Long counterAssetId;

    /** NULL = 미분류. 허용한다 — 기록을 막느니 나중에 채운다(확정 명세 §4.2). */
    @Column(name = "category_id")
    private Long categoryId;

    @Column(length = 120)
    private String title;

    @Column(length = 500)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerTransactionSource source;

    @Column(nullable = false)
    private boolean estimated;

    /** 자동 기록(v1.5)의 출처와 그 회차 예정일. 수동 입력에서는 둘 다 NULL이다. */
    @Column(name = "recurring_id")
    private Long recurringId;

    @Column(name = "occurrence_date")
    private LocalDate occurrenceDate;

    /** 상쇄 대상 원 거래. 이 값이 있으면 이 행은 환불이다. */
    @Column(name = "refund_of_id")
    private Long refundOfId;

    /**
     * 편입된 청구서. 카드 사용 건이 어느 사이클에 들어갔는지이고, <b>산식의 출발점</b>이다.
     * 카드가 아닌 자산의 거래에서는 언제나 {@code null}이다.
     */
    @Column(name = "statement_id")
    private Long statementId;

    /** 할부 원 거래에 붙는다. 회차는 {@code ledger_installment_round}에 따로 있다. */
    @Column(name = "installment_id")
    private Long installmentId;

    /**
     * 어느 가져오기로 들어왔는지(`LDG-093`). <b>손으로 적은 거래에서는 언제나 NULL이다.</b>
     * 배치 되돌리기는 이 값이 있는 행만 건드린다 — 직접 적은 줄이 함께 지워지면
     * 그건 복구가 아니라 사고다.
     */
    @Column(name = "import_batch_id")
    private Long importBatchId;

    /**
     * 어느 여행의 지출인지(여행 v2.2 §3). <b>여행 안에 장부를 만들지 않기 위한 컬럼 하나다</b> —
     * 여행 화면은 이 원장 위의 읽기 뷰이고, 경비는 {@code trip_id}가 그 여행인 행의 합이다.
     *
     * <p>FK가 {@code ON DELETE SET NULL}이라 <b>여행을 지워도 이 행은 남는다.</b> 3만 원을 쓴
     * 것은 여행 밖에서도 사실이라, 연결만 끊고 지출은 원장에 그대로 둔다. 지우는 길이 하나가
     * 아니어서 그 보장을 애플리케이션이 아니라 DB에 맡겼다(D-27).
     */
    @Column(name = "trip_id")
    private Long tripId;

    @Column(name = "fx_currency", length = 3)
    private String fxCurrency;

    @Column(name = "fx_amount", precision = 18, scale = 2)
    private BigDecimal fxAmount;

    @Column(name = "fx_rate", precision = 18, scale = 6)
    private BigDecimal fxRate;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected LedgerTransaction() {
    }

    public LedgerTransaction(Long memberId,
                            LedgerFlow type,
                            LedgerTransactionStatus status,
                            LocalDate occurredOn,
                            long amount,
                            Long assetId,
                            LedgerTransactionSource source) {
        this.memberId = memberId;
        this.type = type;
        this.status = status;
        this.occurredOn = occurredOn;
        this.amount = amount;
        this.assetId = assetId;
        this.source = source;
        this.estimated = false;
    }

    /**
     * 원화 환산액을 외화 근거에서 계산한다. {@code round(fxAmount × fxRate)}.
     *
     * <p>세 값은 <b>함께 있거나 함께 없다</b>(LDG-ERR-021) — 반쪽만 남으면 나중에 검증할 수 없다.
     */
    public static long convertToKrw(BigDecimal fxAmount, BigDecimal fxRate) {
        return fxAmount.multiply(fxRate).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public void updateType(LedgerFlow type) {
        this.type = type;
    }

    public void updateStatus(LedgerTransactionStatus status) {
        this.status = status;
    }

    public void updateOccurredOn(LocalDate occurredOn) {
        this.occurredOn = occurredOn;
    }

    public void updateOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public void updateAmount(long amount) {
        this.amount = amount;
    }

    public void updateAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public void updateCounterAssetId(Long counterAssetId) {
        this.counterAssetId = counterAssetId;
    }

    public void updateCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    public void updateMemo(String memo) {
        this.memo = memo;
    }

    public void updateEstimated(boolean estimated) {
        this.estimated = estimated;
    }

    public void updateRecurrence(Long recurringId, LocalDate occurrenceDate) {
        this.recurringId = recurringId;
        this.occurrenceDate = occurrenceDate;
    }

    public void updateRefundOfId(Long refundOfId) {
        this.refundOfId = refundOfId;
    }

    /**
     * 외화 근거를 채우고 원화 환산액을 다시 계산한다.
     *
     * <p>거래를 수정하면 여기서 다시 계산된다 — 카드사가 실제 적용한 환율이 청구서에 찍힌 뒤
     * 고치는 것이 정상 경로다. 반대로 <b>환율표가 갱신됐다는 이유로는 아무 일도 일어나지 않는다.</b>
     */
    public void applyFx(String currency, BigDecimal fxAmount, BigDecimal fxRate) {
        this.fxCurrency = currency;
        this.fxAmount = fxAmount;
        this.fxRate = fxRate;
        this.amount = convertToKrw(fxAmount, fxRate);
    }

    /** 외화 근거를 지운다. 원화 거래로 되돌리는 경로다 — {@code amount}는 호출자가 정한다. */
    public void clearFx() {
        this.fxCurrency = null;
        this.fxAmount = null;
        this.fxRate = null;
    }

    /** 소프트 삭제. 행을 지우지 않는다 — 되돌릴 수 있어야 하고, 원장은 사실의 기록이다. */
    public void softDelete(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    /**
     * 되살린다. 미납으로 뺐던 회차가 뒤늦게 실제로 빠졌을 때의 경로다.
     *
     * <p>새 행을 넣지 않는다. {@code UNIQUE(recurring_id, occurrence_date)}가 그 자리를
     * 잡고 있기도 하지만, 되살리는 편이 사실에 맞다 — <b>안 빠진 게 아니라 늦게 빠졌다.</b>
     */
    public void restore() {
        this.deletedAt = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean hasFx() {
        return fxCurrency != null;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public LedgerFlow getType() {
        return type;
    }

    public LedgerTransactionStatus getStatus() {
        return status;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public long getAmount() {
        return amount;
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

    public String getTitle() {
        return title;
    }

    public String getMemo() {
        return memo;
    }

    public LedgerTransactionSource getSource() {
        return source;
    }

    public boolean isEstimated() {
        return estimated;
    }

    public Long getRecurringId() {
        return recurringId;
    }

    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }

    public Long getRefundOfId() {
        return refundOfId;
    }

    public void updateStatementId(Long statementId) {
        this.statementId = statementId;
    }

    public Long getStatementId() {
        return statementId;
    }

    public void attachToImportBatch(Long importBatchId) {
        this.importBatchId = importBatchId;
    }

    public Long getImportBatchId() {
        return importBatchId;
    }

    /** 여행에 붙이거나 뗀다. {@code null}이면 연결을 끊는다 — 지출은 그대로 남는다. */
    public void attachToTrip(Long tripId) {
        this.tripId = tripId;
    }

    public Long getTripId() {
        return tripId;
    }

    public void updateInstallmentId(Long installmentId) {
        this.installmentId = installmentId;
    }

    public Long getInstallmentId() {
        return installmentId;
    }

    public String getFxCurrency() {
        return fxCurrency;
    }

    public BigDecimal getFxAmount() {
        return fxAmount;
    }

    public BigDecimal getFxRate() {
        return fxRate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
