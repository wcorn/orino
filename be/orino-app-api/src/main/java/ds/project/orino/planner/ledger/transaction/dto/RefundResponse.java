package ds.project.orino.planner.ledger.transaction.dto;

/**
 * 환불 결과. <b>원 거래는 그대로 남아 있다</b> — 지우지 않고 상쇄한다(확정 명세 §4.3).
 *
 * @param refundedTotal 이 원 거래에 대해 지금까지 환불된 누계
 * @param remaining     아직 환불할 수 있는 금액. 0이면 전액 환불이 끝난 것이다
 */
public record RefundResponse(
        TransactionView refund,
        Long originalTransactionId,
        long refundedTotal,
        long remaining
) {
}
