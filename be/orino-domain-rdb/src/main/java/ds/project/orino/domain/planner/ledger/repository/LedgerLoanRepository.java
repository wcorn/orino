package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerLoan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 대출. 회원 소유 확인은 자산 쪽에서 먼저 한다 — 이 표는 {@code asset_id}만 안다.
 */
public interface LedgerLoanRepository extends JpaRepository<LedgerLoan, Long> {

    /** 삭제 전 확인용 — 이 계좌에서 원금이 빠지는 대출이 있으면 지울 수 없다. */
    boolean existsByPaymentAssetId(Long paymentAssetId);

    /**
     * 대출별 잔여 원금의 재료(데이터 모델 §7.2). 기준 원금 + 기준일 <b>이후</b> 확정 이체.
     *
     * <ul>
     *   <li>대출에서 <b>나간</b> 이체 = 실행 → 원금 +</li>
     *   <li>대출로 <b>들어온</b> 이체 = 상환·중도상환 → 원금 −</li>
     * </ul>
     *
     * <p>잔액 질의({@code sumConfirmedByAssetAndType})와 합치지 않는다 — 이 묶음에만
     * 기준일 필터가 붙어, 한 질의로 섞으면 필터가 새거나 빠진다(아키텍처 §10.3).
     *
     * @param until 이 날까지의 거래만. 지금 잔여 원금이면 오늘을 넘긴다 — 확정 거래는 오늘을
     *              넘지 않는다. 지난 달의 순자산이면 그 달의 끝이다
     */
    @Query("""
            SELECT l.assetId AS assetId,
                   l.baselinePrincipal AS baselinePrincipal,
                   COALESCE(SUM(CASE WHEN t.assetId = l.assetId THEN t.amount
                                     ELSE -t.amount END), 0) AS movement
            FROM LedgerLoan l
            JOIN LedgerAsset a ON a.id = l.assetId
            LEFT JOIN LedgerTransaction t
              ON (t.assetId = l.assetId OR t.counterAssetId = l.assetId)
             AND t.type = ds.project.orino.domain.planner.ledger.entity.LedgerFlow.TRANSFER
             AND t.status = ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus.CONFIRMED
             AND t.deletedAt IS NULL
             AND t.occurredOn > l.baselineAsOf
             AND t.occurredOn <= :until
            WHERE a.memberId = :memberId
            GROUP BY l.assetId, l.baselinePrincipal
            """)
    List<LoanPrincipal> sumPrincipalUpTo(@Param("memberId") Long memberId,
                                         @Param("until") LocalDate until);

    /** 대출 한 건의 잔여 원금 재료. */
    interface LoanPrincipal {
        Long getAssetId();

        long getBaselinePrincipal();

        long getMovement();

        default long principal() {
            return getBaselinePrincipal() + getMovement();
        }
    }
}
