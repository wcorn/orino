package ds.project.orino.domain.planner.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 할부 회차 하나. 청구 관점에서 <b>이 금액이 그 달 청구서에 얹힌다</b>.
 *
 * <p>회차 금액의 합은 원금과 정확히 같다 — 나눠떨어지지 않는 나머지는 <b>첫 회차</b>가 받는다.
 * 카드사도 대개 그렇게 하고, 마지막 회차에 몰면 「끝났는 줄 알았는데 더 나왔다」가 된다.
 */
@Entity
@Table(name = "ledger_installment_round")
public class LedgerInstallmentRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "installment_id", nullable = false)
    private Long installmentId;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "round_no", nullable = false)
    private int roundNo;

    /** `2026-09`. 어느 청구월에 들어가는 회차인지. */
    @Column(name = "billing_month", nullable = false, length = 7)
    private String billingMonth;

    @Column(nullable = false)
    private long amount;

    /** 실제로 편입된 청구서. 아직 그 사이클이 안 열렸으면 {@code null}이다. */
    @Column(name = "statement_id")
    private Long statementId;

    @Column(nullable = false)
    private boolean settled;

    protected LedgerInstallmentRound() {
    }

    public LedgerInstallmentRound(Long installmentId, int roundNo,
                                  String billingMonth, long amount) {
        this.installmentId = installmentId;
        this.roundNo = roundNo;
        this.billingMonth = billingMonth;
        this.amount = amount;
        this.settled = false;
    }

    public void attachTo(Long statementId) {
        this.statementId = statementId;
    }

    public void settle() {
        this.settled = true;
    }

    public Long getId() {
        return id;
    }

    public Long getInstallmentId() {
        return installmentId;
    }

    public int getRoundNo() {
        return roundNo;
    }

    public String getBillingMonth() {
        return billingMonth;
    }

    public long getAmount() {
        return amount;
    }

    public Long getStatementId() {
        return statementId;
    }

    public boolean isSettled() {
        return settled;
    }
}
