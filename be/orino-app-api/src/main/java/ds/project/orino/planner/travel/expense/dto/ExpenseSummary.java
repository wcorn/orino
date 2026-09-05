package ds.project.orino.planner.travel.expense.dto;

/**
 * 여행 하나의 경비 한 줄 — 사이드바 여행 트리와 폴백 화면이 「경비 41.2만」에 쓴다.
 *
 * <p><b>예산을 안 정했으면 {@code budget}이 {@code null}</b>이다. 0을 내리면 화면이
 * 「0원 중 41.2만」을 그린다(명세 v2.2 §5.3) — 경비 화면의 {@code budget} 규칙과 같다.
 *
 * <p>{@code spent}는 <b>확정</b> 지출만이다. 경비 화면의 「썼다」와 같은 값이라야
 * 사이드바를 보고 들어간 사람이 같은 숫자를 다시 본다.
 *
 * @param budget 예산 금액. 안 정했으면 {@code null}
 * @param spent  확정 지출 합계. 한 건도 없으면 0
 */
public record ExpenseSummary(Long budget, long spent) {
}
