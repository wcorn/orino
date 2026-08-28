package ds.project.orino.planner.ledger.transaction.dto;

import java.math.BigDecimal;

/**
 * 외화 입력. 원장에 남는 값은 언제나 원화 환산액이고 이 셋은 그 <b>근거</b>다.
 *
 * @param rate 1 {@code currency}당 원화. {@code null}이면 서버가 ECB 고시로 채운다.
 *             채운 값은 <b>그 거래에 고정</b>되고 조회 시점으로 재계산하지 않는다 —
 *             재계산하면 과거 지출액이 매일 바뀐다(D-9)
 */
public record FxInput(
        String currency,
        BigDecimal amount,
        BigDecimal rate
) {
}
