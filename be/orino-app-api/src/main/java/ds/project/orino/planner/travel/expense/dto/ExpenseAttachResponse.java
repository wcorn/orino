package ds.project.orino.planner.travel.expense.dto;

/**
 * 실제로 붙거나 떨어진 건수.
 *
 * <p>보낸 개수와 다를 수 있다 — 남의 거래·이미 지운 거래는 조용히 빠지고, 떼기는 <b>그 여행에
 * 붙어 있던 것만</b> 센다.
 */
public record ExpenseAttachResponse(int affected) {
}
