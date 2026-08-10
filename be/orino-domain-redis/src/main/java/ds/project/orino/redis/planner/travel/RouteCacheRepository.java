package ds.project.orino.redis.planner.travel;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * 일정 사이 이동시간 캐시.
 *
 * <p>보드는 열 때마다 조회되는데, 같은 두 지점 사이 거리는 잘 변하지 않는다. 캐시가 없으면
 * <b>날짜 탭을 넘길 때마다 유료 호출이 일정 수만큼 난다</b>.
 *
 * <p>키를 여행·일정이 아니라 <b>좌표 쌍과 이동수단</b>으로 잡는다. 그래야 일정 순서를 바꿔도
 * 같은 두 장소 사이 이동은 그대로 재사용되고(§4.4 "순서·장소가 바뀌기 전까지 재조회 안 함"),
 * 여러 여행이 같은 이동시간을 공유한다.
 */
@Repository
public class RouteCacheRepository {

    private static final String PREFIX = "travel:routes:";

    private final StringRedisTemplate redisTemplate;

    public RouteCacheRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<String> find(String legKey) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(PREFIX + legKey));
    }

    public void save(String legKey, String json, Duration ttl) {
        redisTemplate.opsForValue().set(PREFIX + legKey, json, ttl);
    }
}
