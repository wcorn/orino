package ds.project.orino.redis.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "auth:refresh:";
    private static final String GRACE_PREFIX = "auth:refresh:grace:";
    private static final Duration TTL = Duration.ofDays(14);
    // 회전 직후 짧은 유예 창. 거의 동시에 도착한 형제 요청(다중 탭 등)이 방금 회전된 직전
    // 토큰으로도 통과되게 해, 단일 사용 회전이 서로를 무효화하며 강제 로그아웃시키는 걸 막는다.
    private static final Duration GRACE_TTL = Duration.ofSeconds(30);

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

    /** 회전 시 직전 토큰을 짧은 유예로 남긴다(동시 회전 레이스 흡수용). */
    public void saveGrace(Long memberId, String previousToken) {
        redisTemplate.opsForValue().set(GRACE_PREFIX + memberId, previousToken, GRACE_TTL);
    }

    public Optional<String> findGraceByMemberId(Long memberId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(GRACE_PREFIX + memberId));
    }

    public void deleteByMemberId(Long memberId) {
        redisTemplate.delete(KEY_PREFIX + memberId);
        redisTemplate.delete(GRACE_PREFIX + memberId);
    }
}
