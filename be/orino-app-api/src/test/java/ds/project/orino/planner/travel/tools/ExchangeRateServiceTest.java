package ds.project.orino.planner.travel.tools;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.planner.travel.metrics.ExternalApiMetrics;
import ds.project.orino.planner.travel.tools.config.ToolsProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import ds.project.orino.planner.travel.tools.service.ExchangeRateService;
import ds.project.orino.redis.planner.travel.ToolsCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 환율 서비스의 캐시·실패 경로.
 *
 * <p>고시표 캐시는 <b>통화쌍과 무관한 전역 키</b>다(원래 그래야 맞다 — 표는 하루 한 벌이다).
 * 그래서 통합 테스트에서는 앞 테스트가 채운 캐시가 남아 상태를 통제할 수 없다. 여기서는
 * 캐시를 직접 들고 검증한다.
 */
class ExchangeRateServiceTest {

    /** Redis 대신 맵. 이 테스트가 확인하려는 건 "언제 다시 부르나"이지 Redis가 아니다. */
    private static class InMemoryCache extends ToolsCacheRepository {
        private final Map<String, String> store = new HashMap<>();

        InMemoryCache() {
            super(null);
        }

        @Override
        public Optional<String> findRates() {
            return Optional.ofNullable(store.get("rates"));
        }

        @Override
        public void saveRates(String json, Duration ttl) {
            store.put("rates", json);
        }
    }

    private static final ToolsProperties PROPS = new ToolsProperties(
            "https://example.test", "https://example.test/fx.xml",
            Duration.ofHours(24), Duration.ofHours(6), Duration.ofMillis(800),
            Duration.ofHours(24),
            Duration.ofSeconds(5), Duration.ofSeconds(10));

    private StubEcbRatesClient client;
    private ExchangeRateService service;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        client = new StubEcbRatesClient();
        registry = new SimpleMeterRegistry();
        service = new ExchangeRateService(client, new InMemoryCache(), PROPS,
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"),
                ZoneOffset.UTC), new ExternalApiMetrics(registry));
    }

    private double calls(String result) {
        return registry.counter("orino.external.api.calls", "api", "fx", "result", result).count();
    }

    @Test
    @DisplayName("고시표는 한 벌이라 통화쌍이 달라도 다시 받지 않는다")
    void cachesRateTableAcrossPairs() {
        service.rate("JPY", "KRW");
        service.rate("USD", "KRW");
        service.rate("EUR", "JPY");

        assertThat(client.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("히트와 미스를 나눠 센다 — 총 호출만 세면 캐시가 죽어도 그래프가 안 변한다")
    void countsHitAndMiss() {
        service.rate("JPY", "KRW");
        service.rate("USD", "KRW");
        service.rate("EUR", "JPY");

        assertThat(calls("miss")).isEqualTo(1);
        assertThat(calls("hit")).isEqualTo(2);
        // 계측과 실제 호출이 어긋나면 그래프가 거짓말을 한다.
        assertThat(client.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("고시를 못 얻으면 error로 센다 — 나간 호출이라 청구에는 오른다")
    void countsFailureAsError() {
        client.result = Optional.empty();

        try {
            service.rate("JPY", "KRW");
        } catch (RuntimeException expected) {
            // 503으로 떨어지는 것은 아래 테스트가 본다.
        }

        assertThat(calls("error")).isEqualTo(1);
        assertThat(calls("hit")).isZero();
    }

    @Test
    @DisplayName("고시를 못 얻으면 503 — 값이 없는 것과 서비스가 죽은 건 다르다")
    void failsWhenUpstreamUnavailable() {
        client.result = Optional.empty();

        assertThatThrownBy(() -> service.rate("JPY", "KRW"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("환율");
    }

    @Test
    @DisplayName("실패는 캐시하지 않는다 — 24시간 동안 환율이 통째로 막히면 안 된다")
    void doesNotCacheFailure() {
        client.result = Optional.empty();
        assertThatThrownBy(() -> service.rate("JPY", "KRW"))
                .isInstanceOf(CustomException.class);

        client.reset();
        assertThat(service.rate("JPY", "KRW").rate()).isNotNull();
    }

    @Test
    @DisplayName("교차환산 결과를 표시용 자릿수로 자른다")
    void roundsForDisplay() {
        // 1600.00 ÷ 182.64 = 8.760402...
        assertThat(service.rate("JPY", "KRW").rate().toPlainString()).isEqualTo("8.7604");
    }
}
