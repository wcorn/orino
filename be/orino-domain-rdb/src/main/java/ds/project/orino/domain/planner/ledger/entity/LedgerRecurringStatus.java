package ds.project.orino.domain.planner.ledger.entity;

/**
 * 정기 항목의 상태.
 *
 * <p>해지해도 <b>목록에서 사라지지 않는다</b>(확정 명세 §6.6) — 「종료됨」으로 남아야
 * 연간 고정비 회고에서 「올해 이건 넉 달 냈다」가 보인다. 행을 지우면 그 사실이 사라진다.
 */
public enum LedgerRecurringStatus {

    ACTIVE,

    /** 기간을 정해 쉰다. 그 구간의 회차만 전개에서 빠지고 규칙은 그대로다. */
    PAUSED,

    ENDED
}
