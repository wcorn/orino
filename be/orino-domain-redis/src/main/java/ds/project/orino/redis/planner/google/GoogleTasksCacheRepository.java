package ds.project.orino.redis.planner.google;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * 통합 피드 할 일 조회의 단기 캐시. 키 {@code google:tasks-cache:{memberId}:{showCompleted}}.
 *
 * <p>Tasks API는 캐시가 없어 매 요청 라이브 호출되던 지연 요인이었다(#544). 일정 캐시와 동일하게
 * 직렬화된 응답(JSON 문자열)을 단기(~60초) 보관하고, 쓰기 후엔 해당 사용자 캐시를 무효화한다.
 */
@Repository
public class GoogleTasksCacheRepository {

    private static final String KEY_PREFIX = "google:tasks-cache:";

    private final StringRedisTemplate redisTemplate;

    public GoogleTasksCacheRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(Long memberId, boolean showCompleted, String json, Duration ttl) {
        redisTemplate.opsForValue().set(key(memberId, showCompleted), json, ttl);
    }

    public Optional<String> find(Long memberId, boolean showCompleted) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(memberId, showCompleted)));
    }

    /** 해당 사용자의 모든 할 일 캐시를 무효화한다(쓰기 후 호출). 단일 사용자/저트래픽이라 keys 패턴 삭제로 충분. */
    public void evictAll(Long memberId) {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + memberId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String key(Long memberId, boolean showCompleted) {
        return KEY_PREFIX + memberId + ":" + showCompleted;
    }
}
