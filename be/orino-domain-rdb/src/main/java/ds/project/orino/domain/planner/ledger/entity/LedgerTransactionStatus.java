package ds.project.orino.domain.planner.ledger.entity;

/**
 * 확정과 예정.
 *
 * <p>예정은 <b>잔액을 바꾸지 않지만 숨기지도 않는다</b>(확정 명세 §8.3) — 잔액 질의에서는
 * 빠지고, 예상 잔액과 타임라인에는 확정과 같은 줄 위에 남는다.
 */
public enum LedgerTransactionStatus {
    CONFIRMED,
    SCHEDULED
}
