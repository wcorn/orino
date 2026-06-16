package ds.project.orino.redis.planner.google;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * 통합 피드 일정 조회의 단기 캐시. 키 {@code google:cal-cache:{memberId}:{from}:{to}}.
 *
 * <p>뷰 전환/리렌더의 중복 호출(~60초)만 흡수한다. 직렬화된 응답(JSON 문자열)을 그대로 저장하며,
 * 쓰기 후엔 해당 사용자 캐시를 무효화한다(쓰기 프록시 M2 #482에서 evict 추가).
 */
@Repository
public class GoogleCalendarCacheRepository {

    private static final String KEY_PREFIX = "google:cal-cache:";

    private final StringRedisTemplate redisTemplate;

    public GoogleCalendarCacheRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(Long memberId, String from, String to, String json, Duration ttl) {
        redisTemplate.opsForValue().set(key(memberId, from, to), json, ttl);
    }

    public Optional<String> find(Long memberId, String from, String to) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(memberId, from, to)));
    }

    /** 해당 사용자의 모든 기간 캐시를 무효화한다(쓰기 후 호출). 단일 사용자/저트래픽이라 keys 패턴 삭제로 충분. */
    public void evictAll(Long memberId) {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + memberId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String key(Long memberId, String from, String to) {
        return KEY_PREFIX + memberId + ":" + from + ":" + to;
    }
}
