package ds.project.orino.domain.planner.travel.entity;

/**
 * 확정과 예정. 예산 게이지 두 겹이 이 값이다.
 *
 * <p><b>예정을 확정으로 올려 주는 스케줄러를 두지 않는다</b>(경비 독립 §4.3). 가계부에는 그런
 * 배치가 있었지만 여행으로 옮겨 오지 않았다 — 실제로 결제가 일어났는지는 앱이 알 수 없다.
 * 날짜가 지난 예정은 조회가 표시하고({@link TripExpense#isOverdue}) 사람이 누른다.
 *
 * <p>미확정 예정은 사라지지 않는다. 숙소 잔금처럼 아직 안 나간 돈도 예산 게이지 2층에
 * 계속 남아야 「앞으로 얼마 더 나가나」가 보인다.
 */
public enum TripExpenseStatus {
    CONFIRMED,
    SCHEDULED
}
