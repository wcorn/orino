package ds.project.orino.redis.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "auth:refresh:";
    private static final Duration TTL = Duration.ofDays(14);

    private final StringRedisTemplate redisTemplate;

    public RefreshTokenRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(Long memberId, String refreshToken) {
        redisTemplate.opsForValue().set(KEY_PREFIX + memberId, refreshToken, TTL);
    }

    public Optional<String> findByMemberId(Long memberId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + memberId));
    }

    public void deleteByMemberId(Long memberId) {
        redisTemplate.delete(KEY_PREFIX + memberId);
    }
}
