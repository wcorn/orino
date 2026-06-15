package ds.project.orino.redis.planner.google;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * OAuth state ↔ memberId 바인딩 저장소. 키 {@code google:oauth-state:{state}}, TTL 5분.
 *
 * <p>콜백 시점엔 JWT가 없으므로 url 발급 시 state에 memberId를 묶어 저장하고 콜백에서 복원한다(#475).
 * 1회성 단명 저장으로 CSRF 방지를 겸한다.
 */
@Repository
public class GoogleOAuthStateRepository {

    private static final String KEY_PREFIX = "google:oauth-state:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public GoogleOAuthStateRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(String state, Long memberId) {
        redisTemplate.opsForValue().set(key(state), String.valueOf(memberId), TTL);
    }

    public Optional<Long> findMemberId(String state) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(state)))
                .map(Long::valueOf);
    }

    public void delete(String state) {
        redisTemplate.delete(key(state));
    }

    private String key(String state) {
        return KEY_PREFIX + state;
    }
}
