package ds.project.orino.planner.travel.tools.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.planner.travel.metrics.ExternalApiMetrics;
import ds.project.orino.planner.travel.tools.client.EcbRates;
import ds.project.orino.planner.travel.tools.client.EcbRatesClient;
import ds.project.orino.planner.travel.tools.config.ToolsProperties;
import ds.project.orino.planner.travel.tools.dto.ExchangeRateResponse;
import ds.project.orino.redis.planner.travel.ToolsCacheRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Optional;

/**
 * 환율(§S-08).
 *
 * <p>ECB는 <b>EUR 기준으로만</b> 고시한다. JPY↔KRW 같은 쌍은 직접 값이 없어 교차환산해야
 * 하는데, 그 계산이 화면마다 흩어지면 어디선가 반대로 나눈다. 여기서 한 번만 한다.
 */
@Service
public class ExchangeRateService {

    /** 표시용 자릿수. KRW/JPY(≈9.4)도 USD/KRW(≈1300)도 이 정도면 읽힌다. */
    private static final int DISPLAY_SCALE = 4;

    private final EcbRatesClient ratesClient;
    private final ToolsCacheRepository cacheRepository;
    private final ToolsProperties props;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ExternalApiMetrics metrics;

    public ExchangeRateService(EcbRatesClient ratesClient,
                               ToolsCacheRepository cacheRepository,
                               ToolsProperties props,
                               ObjectMapper objectMapper,
                               Clock clock,
                               ExternalApiMetrics metrics) {
        this.ratesClient = ratesClient;
        this.cacheRepository = cacheRepository;
        this.props = props;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.metrics = metrics;
    }

    public ExchangeRateResponse rate(String base, String quote) {
        EcbRates rates = cached().orElseThrow(
                () -> new CustomException(ErrorCode.TRAVEL_FX_UNAVAILABLE));

        BigDecimal value = rates.cross(base, quote).orElseThrow(
                // 고시표에 없는 통화다. 값이 없는 것과 서비스가 죽은 것은 다르다.
                () -> new CustomException(ErrorCode.TRAVEL_FX_UNSUPPORTED_CURRENCY));

        return new ExchangeRateResponse(
                base.toUpperCase(), quote.toUpperCase(),
                value.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP),
                ExchangeRateResponse.SOURCE, rates.referenceDate(), clock.instant());
    }

    /** 고시표는 통화쌍과 무관하게 한 벌이라 쌍마다 캐시하지 않는다. */
    private Optional<EcbRates> cached() {
        Optional<String> hit = cacheRepository.findRates();
        if (hit.isPresent()) {
            metrics.record(ExternalApiMetrics.Api.FX, ExternalApiMetrics.Result.HIT);
            return Optional.of(objectMapper.readValue(hit.get(), EcbRates.class));
        }
        Optional<EcbRates> fresh = ratesClient.latest();
        metrics.recordFetch(ExternalApiMetrics.Api.FX, fresh.isPresent());
        fresh.ifPresent(rates -> cacheRepository.saveRates(
                objectMapper.writeValueAsString(rates), props.fxTtl()));
        return fresh;
    }
}
