package ds.project.orino.core.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm",
                1800000,
                1209600000
        );
        jwtTokenProvider = new JwtTokenProvider(properties);
    }

    @Test
    @DisplayName("Access Token을 생성하고 memberId를 추출한다")
    void createAccessToken_and_getMemberId() {
        String token = jwtTokenProvider.createAccessToken(1L);

        assertThat(jwtTokenProvider.validate(token)).isTrue();
        assertThat(jwtTokenProvider.getMemberId(token)).isEqualTo(1L);
    }

    @Test
    @DisplayName("Refresh Token을 생성하고 memberId를 추출한다")
    void createRefreshToken_and_getMemberId() {
        String token = jwtTokenProvider.createRefreshToken(1L);

        assertThat(jwtTokenProvider.validate(token)).isTrue();
        assertThat(jwtTokenProvider.getMemberId(token)).isEqualTo(1L);
    }

    @Test
    @DisplayName("잘못된 토큰은 검증에 실패한다")
    void validate_invalidToken() {
        assertThat(jwtTokenProvider.validate("invalid.token.value")).isFalse();
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 검증에 실패한다")
    void validate_differentKey() {
        JwtProperties otherProperties = new JwtProperties(
                "other-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm!",
                1800000,
                1209600000
        );
        JwtTokenProvider otherProvider = new JwtTokenProvider(otherProperties);

        String token = otherProvider.createAccessToken(1L);

        assertThat(jwtTokenProvider.validate(token)).isFalse();
    }

    @Test
    @DisplayName("만료된 토큰은 검증에 실패한다")
    void validate_expiredToken() {
        JwtProperties expiredProperties = new JwtProperties(
                "test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm",
                -1000,
                -1000
        );
        JwtTokenProvider expiredProvider = new JwtTokenProvider(expiredProperties);

        String token = expiredProvider.createAccessToken(1L);

        assertThat(jwtTokenProvider.validate(token)).isFalse();
    }
}
