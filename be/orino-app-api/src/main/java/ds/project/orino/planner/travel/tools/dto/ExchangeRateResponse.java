package ds.project.orino.planner.travel.tools.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 환율(§S-08).
 *
 * @param rate          1 {@code base}당 {@code quote} 금액
 * @param referenceDate ECB 고시일. <b>오늘이 아닐 수 있다</b> — 주말·공휴일엔 직전 영업일 값이다
 */
public record ExchangeRateResponse(
        String base,
        String quote,
        BigDecimal rate,
        String source,
        LocalDate referenceDate,
        Instant fetchedAt
) {

    public static final String SOURCE = "ECB";
}
