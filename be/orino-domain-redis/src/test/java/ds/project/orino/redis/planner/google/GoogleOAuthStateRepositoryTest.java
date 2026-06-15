package ds.project.orino.redis.planner.google;

import ds.project.orino.redis.support.RedisTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@RedisTest
class GoogleOAuthStateRepositoryTest {

    @Autowired
    private GoogleOAuthStateRepository oauthStateRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("state ↔ memberId를 저장하고 조회한다")
    void save_and_findMemberId() {
        oauthStateRepository.save("state-abc", 42L);

        assertThat(oauthStateRepository.findMemberId("state-abc")).contains(42L);
    }

    @Test
    @DisplayName("저장 시 5분 TTL이 설정된다")
    void save_setsTtl() {
        oauthStateRepository.save("state-ttl", 1L);

        Long ttl = redisTemplate.getExpire("google:oauth-state:state-ttl");
        assertThat(ttl).isBetween(1L, 300L);
    }

    @Test
    @DisplayName("state를 삭제한다 (콜백 1회성 검증)")
    void delete() {
        oauthStateRepository.save("state-del", 1L);

        oauthStateRepository.delete("state-del");

        assertThat(oauthStateRepository.findMemberId("state-del")).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 state 조회 시 빈 Optional을 반환한다")
    void findMemberId_notFound() {
        assertThat(oauthStateRepository.findMemberId("nonexistent")).isEmpty();
    }
}
