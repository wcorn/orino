package ds.project.orino.planner.ledger.card;

/**
 * 차액 조정 · 수수료 · 할인(확정 명세 §7.4).
 *
 * @param adjustmentAmount     실제 청구액과의 차이(±)
 * @param adjustmentCategoryId 그 차이의 <b>원인</b>. 연회비인지 미반영 건인지가
 *                             「왜 이 금액이지」의 답이다 — 숫자만 남기면 다음 달에 알 수 없다
 * @param interestFeeAmount    리볼빙 수수료·연체 이자. <b>이것만</b> 새 지출이 된다
 * @param discountAmount       청구 할인
 */
public record LedgerStatementAdjustRequest(
        Long adjustmentAmount,
        Long adjustmentCategoryId,
        Long interestFeeAmount,
        Long discountAmount
) {
}
