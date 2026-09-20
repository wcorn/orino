package ds.project.orino.domain.planner.travel.entity;

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

/**
 * 여행 지출 한 줄. <b>원장이 아니라 여행 하나의 지출 목록이다</b>(경비 독립 §2).
 *
 * <p>예전에는 {@code ledger_transaction} 중 {@code trip_id}가 이 여행인 행이었고, 여행 화면은
 * 그 위의 읽기 뷰였다(D-27). 가계부가 없어지면서 읽을 원장이 사라져 여행이 자기 장부를 갖는다.
 *
 * <p><b>가계부였던 부분은 따라오지 않는다.</b> 잔액·자산·복식부기·청구서·정기 항목은 아무것도
 * 없고, 그래서 여기 있는 컬럼은 전부 「무엇에 얼마를 언제 썼나」뿐이다.
 * <ul>
 *   <li>분류는 테이블이 아니라 프리셋 여섯({@link TripExpenseCategory})</li>
 *   <li>결제수단은 자산이 아니라 <b>라벨 한 칸</b> — 잔액을 볼 원장이 사라졌고, 남은 것은
 *       「어디서 냈더라」를 나중에 읽는 일뿐이다</li>
 *   <li>예정은 상태 컬럼 하나({@link TripExpenseStatus})이고 승격 배치가 없다</li>
 * </ul>
 *
 * <p><b>삭제는 지우는 것이지 상쇄가 아니다</b>(§4.4). 「원장은 지우지 않고 반대 거래로
 * 상쇄한다」는 가계부의 규칙은 원장이라서 있던 것이다. 잘못 적었으면 고치거나 지운다 —
 * {@code deletedAt}을 채우되 되돌리기는 화면 토스트에서 즉시만 열고 휴지통을 만들지 않는다.
 *
 * <p>외화는 <b>근거</b>다. 집계가 읽는 값은 언제나 원화 {@code amount}이고 {@code fx*} 셋은
 * 그 값이 어떻게 나왔는지를 설명할 뿐이다. 환율은 저장 시점에 굳고 조회 시 재계산하지
 * 않는다 — 재계산하면 지난 여행의 총액이 매일 바뀐다(§4.3).
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "trip_expense")
public class TripExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 결제한 날. 여행 기간 밖이어도 그대로 둔다 — 항공권은 출발 두 달 전에 산다(D-34). */
    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    /** NULL을 허용한다. 금액만 적고 저장하는 길이 이 기능의 핵심이다(§6.1). */
    @Column(length = 100)
    private String title;

    /**
     * 원화 환산액. <b>집계는 전부 이 값만 읽는다.</b>
     *
     * <p>환율을 못 가져온 외화 건은 0으로 남는다 — 기록을 막지 않으려는 선택이고, 화면이
     * 그 자리에 직접 입력 칸을 연다(§6). 0이라 합계를 부풀리지 않는다.
     */
    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TripExpenseCategory category;

    /** 「국민 체크」 같은 라벨. 자산 테이블이 아니라 사용자가 적은 말이다(§4.2). */
    @Column(name = "payment_method", length = 40)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripExpenseStatus status;

    @Column(name = "fx_currency", length = 3)
    private String fxCurrency;

    @Column(name = "fx_amount", precision = 18, scale = 2)
    private BigDecimal fxAmount;

    /** 쓴 날의 값으로 굳는다. */
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

    protected TripExpense() {
    }

    public TripExpense(Long tripId, Long memberId, LocalDate occurredOn,
                       TripExpenseStatus status) {
        this.tripId = tripId;
        this.memberId = memberId;
        this.occurredOn = occurredOn;
        this.status = status;
    }

    /** 원화 환산액. {@code round(fxAmount × fxRate)}. */
    public static long convertToKrw(BigDecimal fxAmount, BigDecimal fxRate) {
        return fxAmount.multiply(fxRate).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * 원화로만 적는다. 외화 근거가 있었다면 함께 지운다 — 근거와 금액이 따로 놀면
     * 나중에 「이 숫자가 어디서 나왔나」에 답할 수 없다.
     */
    public void applyKrw(long amount) {
        this.amount = amount;
        this.fxCurrency = null;
        this.fxAmount = null;
        this.fxRate = null;
    }

    /**
     * 외화 근거와 함께 적는다. <b>{@code rate}가 없으면 금액은 0</b>이다 — ECB에 닿지 못한
     * 상태이고, 그것 때문에 기록을 막지는 않는다(§6). 화면이 직접 입력 칸을 열고, 사용자가
     * 환율을 채우면 그때 금액이 선다.
     */
    public void applyFx(String currency, BigDecimal fxAmount, BigDecimal fxRate) {
        this.fxCurrency = currency;
        this.fxAmount = fxAmount;
        this.fxRate = fxRate;
        this.amount = fxRate == null ? 0L : convertToKrw(fxAmount, fxRate);
    }

    public void rename(String title) {
        this.title = title;
    }

    public void changeCategory(TripExpenseCategory category) {
        this.category = category;
    }

    /** 앞뒤 공백만 남은 라벨은 「안 적음」과 같은 뜻이라 NULL로 떨어뜨린다. */
    public void changePaymentMethod(String paymentMethod) {
        if (paymentMethod == null) {
            this.paymentMethod = null;
            return;
        }
        String trimmed = paymentMethod.trim();
        this.paymentMethod = trimmed.isEmpty() ? null : trimmed;
    }

    public void changeOccurredOn(LocalDate occurredOn) {
        this.occurredOn = occurredOn;
    }

    public void changeStatus(TripExpenseStatus status) {
        this.status = status;
    }

    public void delete(Instant at) {
        this.deletedAt = at;
    }

    public boolean hasFx() {
        return fxCurrency != null;
    }

    /**
     * 날짜가 지났는데 아직 예정인가. 화면이 「확정」 버튼을 거는 조건이다(§4.3).
     *
     * @param today 그 여행의 오늘. 서버 로컬 날짜를 넘기지 않는다 — 여행 마지막 날 밤에
     *              화면과 판정이 하루 어긋난다
     */
    public boolean isOverdue(LocalDate today) {
        return status == TripExpenseStatus.SCHEDULED && occurredOn.isBefore(today);
    }

    public Long getId() {
        return id;
    }

    public Long getTripId() {
        return tripId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public String getTitle() {
        return title;
    }

    public long getAmount() {
        return amount;
    }

    public TripExpenseCategory getCategory() {
        return category;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public TripExpenseStatus getStatus() {
        return status;
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
