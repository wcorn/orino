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

/**
 * 할부 한 건. 원 거래 1건 + 회차 N행({@link LedgerInstallmentRound})이 여기에 묶인다.
 *
 * <p><b>두 관점의 차이가 가장 크게 벌어지는 곳이다</b>(확정 명세 §10.1).
 * 소비 관점은 원 거래의 전액을 <b>결제일 달</b>에 잡고, 청구 관점은 회차 금액을 <b>각 청구월</b>에
 * 잡는다. 같은 60만원이 한 달에 전부 보이기도 하고 6개월에 10만원씩 보이기도 한다.
 *
 * <p><b>부채는 잔여 원금 전액</b>이다 — 아직 청구되지 않은 회차를 포함하고, 무이자 여부와
 * 무관하다. 갚기로 한 돈은 이미 빚이다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_installment")
public class LedgerInstallment {

    public enum Status {
        ACTIVE,
        /** 남은 회차를 다 냈다. */
        SETTLED,
        /** 중도 취소·상환. 남은 회차는 정리된다. */
        CANCELED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 결제 시점 전액이 적힌 원 거래. 소비 관점은 이 한 건을 본다. */
    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private int months;

    @Column(name = "interest_free", nullable = false)
    private boolean interestFree;

    @Column(nullable = false)
    private long principal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerInstallment() {
    }

    public LedgerInstallment(Long memberId, Long transactionId, int months,
                             boolean interestFree, long principal) {
        this.memberId = memberId;
        this.transactionId = transactionId;
        this.months = months;
        this.interestFree = interestFree;
        this.principal = principal;
        this.status = Status.ACTIVE;
    }

    public void settle() {
        this.status = Status.SETTLED;
    }

    public void cancel() {
        this.status = Status.CANCELED;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public int getMonths() {
        return months;
    }

    public boolean isInterestFree() {
        return interestFree;
    }

    public long getPrincipal() {
        return principal;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
