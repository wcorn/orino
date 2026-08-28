package ds.project.orino.planner.ledger.card;

import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * 결제 처리(확정 명세 §7.3).
 *
 * @param amount         비우면 <b>남은 전액</b>. 일부만 내면 잔액이 다음 청구서로 이월된다
 * @param paymentAssetId 실제로 돈이 빠진 계좌. 비우면 카드에 등록된 결제 계좌
 * @param paidOn         <b>실제 출금일</b>. 비우면 청구서의 결제일이지만, 다르면 실제가 맞다
 */
public record LedgerStatementPayRequest(
        @Positive Long amount,
        Long paymentAssetId,
        LocalDate paidOn
) {
}
