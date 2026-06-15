package ds.project.orino.redis.planner.google;

import ds.project.orino.redis.support.RedisTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@RedisTest
class GoogleAccessTokenRepositoryTest {

    @Autowired
    private GoogleAccessTokenRepository accessTokenRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("access token을 저장하고 조회한다")
    void save_and_find() {
        accessTokenRepository.save(1L, "access-token", Duration.ofMinutes(30));

        assertThat(accessTokenRepository.findByMemberId(1L)).contains("access-token");
    }

    @Test
    @DisplayName("저장 시 TTL이 설정된다")
    void save_setsTtl() {
        accessTokenRepository.save(2L, "access-token", Duration.ofMinutes(30));

        Long ttl = redisTemplate.getExpire("google:access:2");
        assertThat(ttl).isBetween(1L, 1800L);
    }

    @Test
    @DisplayName("access token을 삭제한다")
    void delete() {
        accessTokenRepository.save(3L, "access-token", Duration.ofMinutes(5));

        accessTokenRepository.deleteByMemberId(3L);

        assertThat(accessTokenRepository.findByMemberId(3L)).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 memberId 조회 시 빈 Optional을 반환한다")
    void find_notFound() {
        assertThat(accessTokenRepository.findByMemberId(999L)).isEmpty();
    }
}
