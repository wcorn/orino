package ds.project.orino.planner.travel.tools;

import ds.project.orino.planner.travel.tools.client.EcbRates;
import ds.project.orino.planner.travel.tools.client.EcbRatesClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/** 테스트용 ECB 스텁. 실제 고시값은 매일 바뀌어 단정할 수 없다. */
public class StubEcbRatesClient implements EcbRatesClient {

    public int calls = 0;

    /** 실제 고시표와 같은 형태 — EUR 기준이고 EUR 자신은 없다. */
    public Optional<EcbRates> result = Optional.of(new EcbRates(
            LocalDate.parse("2026-08-07"),
            Map.of("JPY", new BigDecimal("182.64"),
                    "KRW", new BigDecimal("1600.00"),
                    "USD", new BigDecimal("1.1535"))));

    @Override
    public Optional<EcbRates> latest() {
        calls++;
        return result;
    }

    public void reset() {
        calls = 0;
        result = Optional.of(new EcbRates(
                LocalDate.parse("2026-08-07"),
                Map.of("JPY", new BigDecimal("182.64"),
                        "KRW", new BigDecimal("1600.00"),
                        "USD", new BigDecimal("1.1535"))));
    }
}
