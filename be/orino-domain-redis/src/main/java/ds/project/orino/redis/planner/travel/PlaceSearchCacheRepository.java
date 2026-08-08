package ds.project.orino.redis.planner.travel;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * 장소 검색 결과 캐시(§4.7 — 1시간).
 *
 * <p>Places는 <b>호출당 과금</b>이라 같은 검색어를 반복해서 치는 동안 계속 돈이 나간다.
 * 여행 계획은 같은 지역을 여러 번 검색하게 되므로 캐시가 곧 비용 절감이다.
 *
 * <p>직렬화된 JSON 문자열을 그대로 담는다 — 직렬화는 상위 서비스가 맡아 이 모듈이 app-api
 * 타입에 의존하지 않게 한다({@code GeocodeCacheRepository}와 같은 방식).
 */
@Repository
public class PlaceSearchCacheRepository {

    private static final String SEARCH_PREFIX = "travel:places:search:";
    private static final String CITY_PREFIX = "travel:places:city:";

    private final StringRedisTemplate redisTemplate;

    public PlaceSearchCacheRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 검색 캐시. 편향 좌표가 다르면 결과도 달라지므로 키에 포함한다. */
    public Optional<String> findSearch(String query, String biasKey) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(searchKey(query, biasKey)));
    }

    public void saveSearch(String query, String biasKey, String json, Duration ttl) {
        redisTemplate.opsForValue().set(searchKey(query, biasKey), json, ttl);
    }

    public Optional<String> findCity(String query) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(CITY_PREFIX + query));
    }

    public void saveCity(String query, String json, Duration ttl) {
        redisTemplate.opsForValue().set(CITY_PREFIX + query, json, ttl);
    }

    private String searchKey(String query, String biasKey) {
        return SEARCH_PREFIX + biasKey + ":" + query;
    }
}
