package ds.project.orino.domain.planner.ledger.entity;

/** 월 시작일이 주말·공휴일에 걸릴 때의 처리. 예산 기간에만 쓴다(확정 명세 §9). */
public enum LedgerMonthStartWeekendPolicy {
    AS_IS,
    PREV_BUSINESS_DAY
}
