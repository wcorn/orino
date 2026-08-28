package ds.project.orino.domain.planner.ledger.entity;

/**
 * 정기 항목의 종류(확정 명세 §6.1).
 *
 * <p><b>표시·필터용 라벨일 뿐이다.</b> 주기 계산도, 예정 생성도, 확정도 종류에 따라 갈리지
 * 않는다 — 구독과 보험료와 자동이체는 「정해진 날에 정해진 돈이 나간다」는 같은 사실이고,
 * 종류마다 다른 코드 경로를 두면 같은 버그를 다섯 번 고치게 된다.
 */
public enum LedgerRecurringKind {

    SUBSCRIPTION,
    FIXED_COST,
    INSURANCE,
    TRANSFER,
    INCOME
}
