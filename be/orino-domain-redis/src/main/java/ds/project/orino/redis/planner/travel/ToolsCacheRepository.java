package ds.project.orino.redis.planner.travel;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * 날씨·환율 캐시(§4.7 — 날씨 6h · 환율 24h).
 *
 * <p>둘 다 무료 API라 비용이 들지는 않는다. 그래도 캐시하는 이유는 둘이다 —
 * 남의 무료 서비스를 필요 이상으로 두드리지 않는 것, 그리고 예보·고시가 그보다 자주 바뀌지
 * 않아 <b>다시 부를 이유 자체가 없다</b>는 것.
 */
@Repository
public class ToolsCacheRepository {

    private static final String WEATHER_PREFIX = "travel:weather:";
    private static final String FX_PREFIX = "travel:fx:";

    private final StringRedisTemplate redisTemplate;

    public ToolsCacheRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 좌표·타임존이 다르면 예보도 다르다 — 키에 전부 넣는다. */
    public Optional<String> findWeather(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(WEATHER_PREFIX + key));
    }

    public void saveWeather(String key, String json, Duration ttl) {
        redisTemplate.opsForValue().set(WEATHER_PREFIX + key, json, ttl);
    }

    /** 고시표는 통화쌍과 무관하게 한 벌이다 — 쌍마다 캐시하지 않는다. */
    public Optional<String> findRates() {
        return Optional.ofNullable(redisTemplate.opsForValue().get(FX_PREFIX + "ecb"));
    }

    public void saveRates(String json, Duration ttl) {
        redisTemplate.opsForValue().set(FX_PREFIX + "ecb", json, ttl);
    }
}
