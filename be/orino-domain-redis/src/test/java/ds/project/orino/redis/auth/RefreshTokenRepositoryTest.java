package ds.project.orino.redis.auth;

import ds.project.orino.redis.support.RedisTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@RedisTest
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("Refresh Token을 저장하고 조회한다")
    void save_and_find() {
        refreshTokenRepository.save(1L, "test-refresh-token");

        Optional<String> found = refreshTokenRepository.findByMemberId(1L);

        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo("test-refresh-token");
    }

    @Test
    @DisplayName("Refresh Token을 삭제한다")
    void delete() {
        refreshTokenRepository.save(2L, "to-be-deleted");

        refreshTokenRepository.deleteByMemberId(2L);

        assertThat(refreshTokenRepository.findByMemberId(2L)).isEmpty();
    }

    @Test
    @DisplayName("새 토큰 저장 시 기존 토큰을 덮어쓴다")
    void save_overwrite() {
        refreshTokenRepository.save(3L, "old-token");
        refreshTokenRepository.save(3L, "new-token");

        Optional<String> found = refreshTokenRepository.findByMemberId(3L);

        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo("new-token");
    }

    @Test
    @DisplayName("존재하지 않는 memberId 조회 시 빈 Optional을 반환한다")
    void findByMemberId_notFound() {
        assertThat(refreshTokenRepository.findByMemberId(999L)).isEmpty();
    }
}
