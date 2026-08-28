package ds.project.orino.planner.ledger.transaction.dto;

import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;

/**
 * 입력 결과.
 *
 * @param savedAs 실제로 저장된 상태. 미래 날짜를 적으면 요청과 달리 {@code SCHEDULED}가 된다 —
 *                화면은 그 사실을 사용자에게 알린다(확정 명세 §4.2)
 */
public record TransactionCreatedResponse(
        TransactionView transaction,
        LedgerTransactionStatus savedAs
) {
}
