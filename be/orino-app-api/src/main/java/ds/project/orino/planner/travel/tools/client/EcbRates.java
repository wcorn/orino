package ds.project.orino.planner.travel.tools.client;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * ECB 고시 환율 한 벌. <b>전부 EUR 기준</b>이다.
 *
 * @param referenceDate 고시일. 주말·공휴일엔 직전 영업일이다
 * @param perEur        통화 → 1 EUR당 금액. EUR 자신은 들어 있지 않다
 */
public record EcbRates(LocalDate referenceDate, Map<String, BigDecimal> perEur) {

    private static final String EURO = "EUR";
    /** 표시용이라 소수 6자리면 충분하다. KRW/JPY 같은 큰 비율도 감당한다. */
    private static final MathContext PRECISION = new MathContext(10, RoundingMode.HALF_UP);

    /**
     * 교차환산 — 1 {@code base}가 몇 {@code quote}인가.
     *
     * <p>ECB는 EUR 기준으로만 고시하므로 JPY→KRW 같은 쌍은 직접 값이 없다.
     * {@code (KRW/EUR) ÷ (JPY/EUR)}로 EUR을 소거한다.
     */
    public Optional<BigDecimal> cross(String base, String quote) {
        Optional<BigDecimal> basePerEur = rateOf(base);
        Optional<BigDecimal> quotePerEur = rateOf(quote);
        if (basePerEur.isEmpty() || quotePerEur.isEmpty()
                || basePerEur.get().signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(quotePerEur.get().divide(basePerEur.get(), PRECISION));
    }

    /** EUR은 고시표에 없다 — 자기 자신이라 1이다. */
    private Optional<BigDecimal> rateOf(String currency) {
        if (EURO.equalsIgnoreCase(currency)) {
            return Optional.of(BigDecimal.ONE);
        }
        return Optional.ofNullable(perEur.get(currency.toUpperCase()));
    }
}
