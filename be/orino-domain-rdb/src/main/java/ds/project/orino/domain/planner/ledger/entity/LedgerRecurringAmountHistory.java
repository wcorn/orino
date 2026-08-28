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

import java.time.Instant;
import java.time.LocalDate;

/**
 * 금액이 언제 얼마로 바뀌었는지.
 *
 * <p>「2026-03 12,000 → 17,000 (+41%)」를 만드는 표다. 점검 신호 ①(최근 인상)과
 * ②(6개월 이상 미변동)가 여기서 나온다 — <b>구독료가 조용히 오르는 것이 구독 관리의
 * 핵심 문제</b>이고, 현재 금액만 갖고 있으면 오른 사실 자체를 알 수 없다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_recurring_amount_history")
public class LedgerRecurringAmountHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recurring_id", nullable = false)
    private Long recurringId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false)
    private long amount;

    @CreatedDate
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected LedgerRecurringAmountHistory() {
    }

    public LedgerRecurringAmountHistory(Long recurringId, LocalDate effectiveFrom, long amount) {
        this.recurringId = recurringId;
        this.effectiveFrom = effectiveFrom;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Long getRecurringId() {
        return recurringId;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public long getAmount() {
        return amount;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
