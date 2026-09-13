package ds.project.orino.domain.planner.ledger.entity;

/**
 * 대출 상환 방식(덧붙인 명세 §4.3). 거치기간·체증식·혼합형은 이번에 넣지 않는다(§2).
 */
public enum LedgerRepaymentMethod {
    /** 원리금균등 — 매 회차 같은 금액, 원금·이자 비율이 달마다 바뀐다. */
    EQUAL_PAYMENT,
    /** 원금균등 — 매 회차 같은 원금, 이자가 줄어든다. */
    EQUAL_PRINCIPAL,
    /** 만기일시 — 이자만 내다가 만기 회차에 원금 전액. */
    BULLET
}
