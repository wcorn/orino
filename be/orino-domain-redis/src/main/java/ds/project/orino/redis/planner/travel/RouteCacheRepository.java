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

    /**
     * "이 구간은 경로가 없다"를 기억하는 자리(#1203).
     *
     * <p>같은 키에 특별한 값을 넣지 않고 <b>접두사를 따로 두는</b> 이유는, 기존 항목이 담고
     * 있는 JSON 형식을 건드리지 않기 위해서다. 같은 자리에 sentinel 을 섞으면 배포 순간부터
     * 이미 캐시된 값들이 파싱 예외를 낸다. legKey 는 좌표로 시작하므로 두 접두사가 겹칠 수도
     * 없다.
     */
    private static final String NO_ROUTE_PREFIX = "travel:routes:noroute:";

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

    /** 경로가 없다고 기억해 둔 구간인가. */
    public boolean isKnownNoRoute(String legKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(NO_ROUTE_PREFIX + legKey));
    }

    /** 값은 쓰지 않는다 — 키의 존재 자체가 답이다. */
    public void saveNoRoute(String legKey, Duration ttl) {
        redisTemplate.opsForValue().set(NO_ROUTE_PREFIX + legKey, "1", ttl);
    }
}
