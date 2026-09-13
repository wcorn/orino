package ds.project.orino.domain.planner.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 금리 이력(데이터 모델 §7.4). 대출·마이너스통장 공용이다.
 *
 * <p>변동금리가 바뀌면 행을 <b>덮지 않고 더한다</b> — 적용일 이후 회차만 새 금리로 계산하고,
 * 지난 이자는 이미 원장에 있다. 회차 날짜의 금리는 {@code effective_from ≤ 날짜}인 행 중
 * 가장 늦은 것이다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_rate_history")
public class LedgerRateHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** 연 %(예: {@code 3.850}). 금액이 아니라 원 단위 정수 규칙 대상이 아니다. */
    @Column(name = "annual_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal annualRate;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerRateHistory() {
    }

    public LedgerRateHistory(Long assetId, LocalDate effectiveFrom, BigDecimal annualRate) {
        this.assetId = assetId;
        this.effectiveFrom = effectiveFrom;
        this.annualRate = annualRate;
    }

    /** 같은 적용일을 다시 적으면 그 행의 금리만 고친다 — 잘못 적은 금리를 행 두 개로 남기지 않는다. */
    public void updateAnnualRate(BigDecimal annualRate) {
        this.annualRate = annualRate;
    }

    public Long getId() {
        return id;
    }

    public Long getAssetId() {
        return assetId;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
