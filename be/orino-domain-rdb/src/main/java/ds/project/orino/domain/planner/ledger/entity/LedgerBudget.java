package ds.project.orino.domain.planner.ledger.entity;

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

import java.time.Instant;
import java.time.LocalDate;

/**
 * 한 기간의 예산.
 *
 * <p><b>구간을 계산하지 않고 저장한다.</b> 월 시작일을 나중에 25일에서 1일로 바꾸면 과거
 * 예산의 구간이 소급해서 달라지고, 그러면 「지난달 같은 시점 대비」가 거짓말이 된다 —
 * 지난달에 내가 세운 예산은 그때의 구간에 대한 것이었다.
 *
 * <p>{@code period}는 <b>시작한 달</b>의 이름이다. 25일 시작이면 7/25~8/24가 「2026-07」이다 —
 * 「7월 급여로 사는 기간」이 사람이 그 구간을 부르는 이름이기 때문이다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_budget")
public class LedgerBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 7)
    private String period;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerBudget() {
    }

    public LedgerBudget(Long memberId, String period, LocalDate periodStart,
                        LocalDate periodEnd, long totalAmount) {
        this.memberId = memberId;
        this.period = period;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.totalAmount = totalAmount;
    }

    public void updateTotalAmount(long totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getPeriod() {
        return period;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
