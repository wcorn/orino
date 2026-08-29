package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerInstallment;
import ds.project.orino.domain.planner.ledger.entity.LedgerInstallmentRound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LedgerInstallmentRoundRepository
        extends JpaRepository<LedgerInstallmentRound, Long> {

    List<LedgerInstallmentRound> findAllByInstallmentIdOrderByRoundNoAsc(Long installmentId);

    List<LedgerInstallmentRound> findAllByStatementId(Long statementId);

    /** 아직 청구서에 안 붙은 그 달 회차들. 사이클이 열릴 때 붙인다. */
    @Query("""
            SELECT r FROM LedgerInstallmentRound r
            WHERE r.installmentId IN :installmentIds
              AND r.billingMonth = :billingMonth
              AND r.statementId IS NULL
            """)
    List<LedgerInstallmentRound> findUnattached(
            @Param("installmentIds") Collection<Long> installmentIds,
            @Param("billingMonth") String billingMonth);

    /**
     * 아직 <b>어느 청구서에도 안 붙은</b> 남은 회차. 예정 목록의 네 번째 출처다.
     *
     * <p>붙은 회차를 함께 세면 안 된다 — 그건 이미 그 청구서의 청구액에 들어가 있고,
     * 카드 결제 예정이 같은 돈을 한 번 더 세게 된다.
     */
    @Query("""
            SELECT r FROM LedgerInstallmentRound r
            WHERE r.settled = false
              AND r.statementId IS NULL
              AND r.installmentId IN (
                  SELECT i.id FROM LedgerInstallment i
                  WHERE i.memberId = :memberId AND i.status = :status)
            ORDER BY r.billingMonth ASC, r.roundNo ASC
            """)
    List<LedgerInstallmentRound> findUnbilledByMember(@Param("memberId") Long memberId,
                                                      @Param("status") LedgerInstallment.Status status);

    /**
     * 잔여 원금 — 아직 내지 않은 회차의 합.
     *
     * <p><b>청구 여부와 무관하다.</b> 아직 청구되지 않은 회차도 이미 갚기로 한 돈이라
     * 부채에 들어간다(확정 명세 §5.3).
     */
    @Query("""
            SELECT COALESCE(SUM(r.amount), 0) FROM LedgerInstallmentRound r
            WHERE r.installmentId IN :installmentIds AND r.settled = false
            """)
    long sumOutstanding(@Param("installmentIds") Collection<Long> installmentIds);
}
