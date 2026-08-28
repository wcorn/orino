package ds.project.orino.domain.planner.ledger.entity;

/** 자산 유형(확정 명세 §5.1). 부채(LOAN)는 v2다. */
public enum LedgerAssetType {
    CASH,
    CHECKING,
    SAVINGS,
    DEBIT_CARD,
    CREDIT_CARD,
    PREPAID;

    /**
     * 잔액을 자기 이름으로 갖는 자산인지.
     *
     * <p>체크카드는 아니다 — 거래는 체크카드에 붙지만 잔액은 연결 계좌에서 빠진다(D-4).
     * 신용카드도 아니다 — 사용액은 잔액이 아니라 <b>청구서</b>가 된다(v1.5).
     */
    public boolean holdsBalance() {
        return this == CASH || this == CHECKING || this == SAVINGS || this == PREPAID;
    }
}
