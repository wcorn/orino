package ds.project.orino.planner.travel.expense.dto;

import java.math.BigDecimal;

/**
 * 외화 입력 — 「엔으로 적고 원으로 센다」. 저장되는 값은 언제나 원화 환산액이고 이 셋은
 * 그 <b>근거</b>다.
 *
 * @param rate 1 {@code currency}당 원화. {@code null}이면 서버가 ECB 고시로 채우고
 *             <b>그 값을 저장 시점에 고정</b>한다 — 조회 시점으로 재계산하면 지난 여행의
 *             총액이 매일 바뀐다(경비 독립 §4.3)
 */
public record ExpenseFxInput(
        String currency,
        BigDecimal amount,
        BigDecimal rate
) {
}
