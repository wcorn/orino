package ds.project.orino.domain.planner.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 대출 속성(`LDG-008`). 자산과 1:1이라 {@code asset_id}가 곧 PK다(D-20).
 *
 * <p><b>잔여 원금 컬럼이 없다.</b> 기준 원금 + 기준일 이후 이체로 조회할 때 센다(D-8).
 * 기준 원금은 원장으로 계산할 수 없는 은행 조회 값이라 여기 둔다(D-18) — 1억짜리 과거 대출을
 * 조정 거래로 세우면 그 1억이 어느 달의 수입이나 지출로 원장에 남는다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_loan")
public class LedgerLoan {

    @Id
    @Column(name = "asset_id")
    private Long assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_method", nullable = false, length = 30)
    private LedgerRepaymentMethod repaymentMethod;

    /** 출금 계좌. 상환 처리의 기본값이지 강제는 아니다 — 그날 다른 통장에서 냈다면 그쪽이 맞다. */
    @Column(name = "payment_asset_id", nullable = false)
    private Long paymentAssetId;

    /** 1~28 또는 99(말일). DB가 TINYINT라 타입 코드를 맞춘다 — 안 맞추면 validate가 깨진다. */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "payment_day", nullable = false)
    private int paymentDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_day_policy", nullable = false, length = 20)
    private LedgerBusinessDayPolicy businessDayPolicy;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    /** 약정 원금 — 표시용이다. 계산은 이 값을 읽지 않는다. */
    @Column(name = "original_principal")
    private Long originalPrincipal;

    @Column(name = "baseline_principal", nullable = false)
    private long baselinePrincipal;

    /** 기준일. <b>이 날까지의 실행·상환은 기준 원금에 이미 들어 있다.</b> */
    @Column(name = "baseline_as_of", nullable = false)
    private LocalDate baselineAsOf;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerLoan() {
    }

    public LedgerLoan(Long assetId, LedgerRepaymentMethod repaymentMethod, Long paymentAssetId,
                      int paymentDay, LedgerBusinessDayPolicy businessDayPolicy,
                      LocalDate startedOn, LocalDate maturityDate, Long originalPrincipal,
                      long baselinePrincipal, LocalDate baselineAsOf) {
        this.assetId = assetId;
        this.repaymentMethod = repaymentMethod;
        this.paymentAssetId = paymentAssetId;
        this.paymentDay = paymentDay;
        this.businessDayPolicy = businessDayPolicy;
        this.startedOn = startedOn;
        this.maturityDate = maturityDate;
        this.originalPrincipal = originalPrincipal;
        this.baselinePrincipal = baselinePrincipal;
        this.baselineAsOf = baselineAsOf;
    }

    /** 은행 앱의 잔여 원금으로 다시 맞춘다. 거래를 만들지 않는다(D-18). */
    public void updateBaseline(long baselinePrincipal, LocalDate baselineAsOf) {
        this.baselinePrincipal = baselinePrincipal;
        this.baselineAsOf = baselineAsOf;
    }

    public Long getAssetId() {
        return assetId;
    }

    public LedgerRepaymentMethod getRepaymentMethod() {
        return repaymentMethod;
    }

    public Long getPaymentAssetId() {
        return paymentAssetId;
    }

    public int getPaymentDay() {
        return paymentDay;
    }

    public LedgerBusinessDayPolicy getBusinessDayPolicy() {
        return businessDayPolicy;
    }

    public LocalDate getStartedOn() {
        return startedOn;
    }

    public LocalDate getMaturityDate() {
        return maturityDate;
    }

    public Long getOriginalPrincipal() {
        return originalPrincipal;
    }

    public long getBaselinePrincipal() {
        return baselinePrincipal;
    }

    public LocalDate getBaselineAsOf() {
        return baselineAsOf;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
