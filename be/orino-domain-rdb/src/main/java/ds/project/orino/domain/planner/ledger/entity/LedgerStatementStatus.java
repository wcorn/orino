package ds.project.orino.domain.planner.ledger.entity;

/**
 * 청구서의 상태(확정 명세 §7.1).
 *
 * <p><b>「미납」이 여기 없다.</b> 미납은 상태가 아니라 <b>사실</b>이다 — 결제일이 지났는데
 * 아직 안 냈다는 것. 저장해 두면 날짜가 바뀔 때마다 누군가 갱신해야 하고, 갱신을 놓치는
 * 순간 화면이 거짓말을 한다. 잔액을 컬럼으로 두지 않은 것과 같은 이유다(D-8).
 */
public enum LedgerStatementStatus {

    /** 사이클이 열려 있다. 이 카드로 쓰는 건이 계속 편입된다. */
    COLLECTING,

    /** 마감일이 지나 금액이 더 이상 늘지 않는다. 사람이 결제 처리를 하기 전. */
    CONFIRMED,

    /** 일부만 냈다. 남은 잔액은 부채로 남아 다음 청구서로 이월된다. */
    PARTIAL,

    PAID;

    /** 아직 돈이 다 나가지 않은 상태. 미납 판정의 전제다. */
    public boolean isUnsettled() {
        return this == CONFIRMED || this == PARTIAL;
    }
}
