package ds.project.orino.domain.planner.ledger.entity;

/** 자산 유형(확정 명세 §5.1 · 덧붙인 명세 §4). 유형은 바꿀 수 없다 — 쌓인 거래의 의미가 바뀐다. */
public enum LedgerAssetType {
    CASH,
    CHECKING,
    SAVINGS,
    DEBIT_CARD,
    CREDIT_CARD,
    PREPAID,
    /**
     * 대출. 잔액 대신 <b>잔여 원금</b>을 갖고 그건 부채다. 붙는 거래는 <b>이체뿐</b>이다
     * (LDG-ERR-039) — 원금이 이체 말고 다른 길로 움직이면 잔여 원금과 통계가 함께 틀어진다.
     */
    LOAN;

    /**
     * 잔액을 자기 이름으로 갖는 자산인지.
     *
     * <p>체크카드는 아니다 — 거래는 체크카드에 붙지만 잔액은 연결 계좌에서 빠진다(D-4).
     * 신용카드도 아니다 — 사용액은 잔액이 아니라 <b>청구서</b>가 된다(v1.5).
     * 대출도 아니다 — 잔여 원금은 기준값 + 기준일 이후 이체라 따로 센다(§4.2). 그래서 「쓸 수
     * 있는 돈」·잔액 맞추기·연결 계좌 후보에서 저절로 빠진다.
     */
    public boolean holdsBalance() {
        return this == CASH || this == CHECKING || this == SAVINGS || this == PREPAID;
    }
}
