package ds.project.orino.planner.travel.expense.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 여행에 붙이거나 뗄 거래들(명세 v2.2 §18).
 *
 * <p>남의 거래·이미 지운 거래는 <b>조용히 빠진다</b> — 그래서 응답의 {@code affected}가 보낸
 * 개수와 다를 수 있다. 가계부 일괄 편집이 쓰는 규칙과 같다.
 */
public record ExpenseAttachRequest(
        @NotEmpty List<Long> transactionIds
) {
}
