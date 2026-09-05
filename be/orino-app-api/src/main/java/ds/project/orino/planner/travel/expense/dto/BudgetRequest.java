package ds.project.orino.planner.travel.expense.dto;

/**
 * 여행 예산. <b>{@code null}이면 해제</b>다.
 *
 * <p>{@code @NotNull}을 걸지 않는 이유가 그것이다 — 「안 정함」이 유효한 상태이고, 그 상태로
 * 되돌리는 길이 이 요청 말고는 없다. 0은 해제가 아니라 400이다(§5.3).
 */
public record BudgetRequest(Long amount) {
}
