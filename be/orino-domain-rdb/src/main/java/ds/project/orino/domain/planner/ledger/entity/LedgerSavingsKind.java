package ds.project.orino.domain.planner.ledger.entity;

/**
 * 예·적금의 종류(D-15). {@code null}이면 일반 예·적금이다.
 *
 * <p>청약을 새 유형으로 열지 않은 이유 — 잔액·총자산·「쓸 수 있는 돈」 제외까지 예·적금으로
 * 이미 맞다. 유형을 늘리면 그 분기마다 청약을 한 번씩 더 적어야 하고, 빠뜨린 곳에서 청약이
 * 「쓸 수 있는 돈」에 섞인다.
 */
public enum LedgerSavingsKind {
    /** 주택청약종합저축. 인정 회차·금액을 추정해 보여준다. */
    HOUSING_SUBSCRIPTION
}
