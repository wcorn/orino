package ds.project.orino.redis.planner.lifelog;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * 지오코딩 결과 캐시. Nominatim 정책상 캐시가 의무이며, 같은 좌표/검색어의 반복 호출을 흡수한다.
 *
 * <p>직렬화된 JSON 문자열을 그대로 저장한다(직렬화는 상위 서비스가 담당 — 도메인-redis 모듈이
 * app-api 타입에 의존하지 않게). 키는 좌표를 반올림해(≈11m) 근처 좌표의 캐시 히트를 높인다.
 */
@Repository
public class GeocodeCacheRepository {

    private static final String REVERSE_PREFIX = "lifelog:geocode:rev:";
    private static final String SEARCH_PREFIX = "lifelog:geocode:search:";

    private final StringRedisTemplate redisTemplate;

    public GeocodeCacheRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<String> findReverse(String latKey, String lngKey) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(reverseKey(latKey, lngKey)));
    }

    public void saveReverse(String latKey, String lngKey, String json, Duration ttl) {
        redisTemplate.opsForValue().set(reverseKey(latKey, lngKey), json, ttl);
    }

    public Optional<String> findSearch(String query, int limit) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(searchKey(query, limit)));
    }

    public void saveSearch(String query, int limit, String json, Duration ttl) {
        redisTemplate.opsForValue().set(searchKey(query, limit), json, ttl);
    }

    private String reverseKey(String latKey, String lngKey) {
        return REVERSE_PREFIX + latKey + ":" + lngKey;
    }

    private String searchKey(String query, int limit) {
        return SEARCH_PREFIX + limit + ":" + query;
    }
}
