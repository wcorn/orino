package ds.project.orino.planner.ledger.fx;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 환율 조회 응답.
 *
 * @param rate          1 {@code currency}당 원화. <b>{@code null}일 수 있다</b> — ECB에 닿지
 *                      못했다는 뜻이고, 화면은 그때 직접 입력을 받는다. 에러가 아니다
 * @param referenceDate ECB 고시일. 주말·공휴일이면 직전 영업일이다
 */
public record LedgerFxRateResponse(
        String currency,
        BigDecimal rate,
        LocalDate referenceDate,
        String source
) {
}
