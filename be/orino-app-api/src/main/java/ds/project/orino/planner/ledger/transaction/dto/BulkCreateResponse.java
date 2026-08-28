package ds.project.orino.planner.ledger.transaction.dto;

import java.util.List;

/**
 * 다건 입력 결과.
 *
 * <p>한 트랜잭션이므로 <b>전부 들어갔거나 하나도 안 들어갔거나</b> 둘 중 하나다 —
 * 「7건 성공 3건 실패」 같은 응답이 없는 것이 이 API의 요지다.
 *
 * @param scheduledCount 그중 미래 날짜라 예정으로 저장된 건수. 화면이 그 사실을 알린다
 */
public record BulkCreateResponse(
        List<TransactionView> created,
        int scheduledCount
) {
}
