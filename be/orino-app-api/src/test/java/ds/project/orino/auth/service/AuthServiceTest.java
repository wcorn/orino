package ds.project.orino.auth.service;

import ds.project.orino.auth.dto.LoginRequest;
import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.core.jwt.JwtTokenProvider;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.redis.auth.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Test
    @DisplayName("로그인 성공 시 AT와 RT를 반환한다")
    void login_success() {
        Member member = new Member("admin", "encoded");
        given(memberRepository.findByLoginId("admin")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("password", "encoded")).willReturn(true);
        given(jwtTokenProvider.createAccessToken(any())).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(any())).willReturn("refresh-token");

        AuthService.LoginResult result = authService.login(new LoginRequest("admin", "password"));

        assertThat(result.tokenResponse().accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenRepository).save(any(), eq("refresh-token"));
    }

    @Test
    @DisplayName("존재하지 않는 아이디로 로그인 시 예외를 던진다")
    void login_invalidLoginId() {
        given(memberRepository.findByLoginId("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown", "password")))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    @DisplayName("비밀번호 불일치 시 예외를 던진다")
    void login_invalidPassword() {
        Member member = new Member("admin", "encoded");
        given(memberRepository.findByLoginId("admin")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("wrong", "encoded")).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong")))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    @DisplayName("토큰 갱신 성공 시 새 AT와 RT를 반환한다")
    void reissue_success() {
        given(jwtTokenProvider.validate("old-rt")).willReturn(true);
        given(jwtTokenProvider.getMemberId("old-rt")).willReturn(1L);
        given(refreshTokenRepository.findByMemberId(1L)).willReturn(Optional.of("old-rt"));
        given(jwtTokenProvider.createAccessToken(1L)).willReturn("new-at");
        given(jwtTokenProvider.createRefreshToken(1L)).willReturn("new-rt");

        AuthService.LoginResult result = authService.reissue("old-rt");

        assertThat(result.tokenResponse().accessToken()).isEqualTo("new-at");
        assertThat(result.refreshToken()).isEqualTo("new-rt");
        verify(refreshTokenRepository).save(1L, "new-rt");
    }

    @Test
    @DisplayName("유효하지 않은 RT로 갱신 시 예외를 던진다")
    void reissue_invalidToken() {
        given(jwtTokenProvider.validate("invalid")).willReturn(false);

        assertThatThrownBy(() -> authService.reissue("invalid"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    @DisplayName("로그아웃 시 RT를 삭제한다")
    void logout_success() {
        given(jwtTokenProvider.validate("rt")).willReturn(true);
        given(jwtTokenProvider.getMemberId("rt")).willReturn(1L);

        authService.logout("rt");

        verify(refreshTokenRepository).deleteByMemberId(1L);
    }
}
