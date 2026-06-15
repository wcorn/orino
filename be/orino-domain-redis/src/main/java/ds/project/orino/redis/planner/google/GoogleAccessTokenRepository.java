package ds.project.orino.redis.planner.google;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * Google access token 캐시. 키 {@code google:access:{memberId}}, TTL = expires_in(여유 차감).
 *
 * <p>access token은 단명(~1h)이라 Redis 자동 만료가 적합하다. miss/401 시 {@code GoogleTokenProvider}가
 * refresh grant로 재발급해 다시 캐시한다(#476).
 */
@Repository
public class GoogleAccessTokenRepository {

    private static final String KEY_PREFIX = "google:access:";

    private final StringRedisTemplate redisTemplate;

    public GoogleAccessTokenRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(Long memberId, String accessToken, Duration ttl) {
        redisTemplate.opsForValue().set(key(memberId), accessToken, ttl);
    }

    public Optional<String> findByMemberId(Long memberId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(memberId)));
    }

    public void deleteByMemberId(Long memberId) {
        redisTemplate.delete(key(memberId));
    }

    private String key(Long memberId) {
        return KEY_PREFIX + memberId;
    }
}
