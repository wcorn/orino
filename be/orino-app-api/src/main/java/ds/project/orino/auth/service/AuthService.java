package ds.project.orino.auth.service;

import ds.project.orino.auth.dto.LoginRequest;
import ds.project.orino.auth.dto.TokenResponse;
import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.core.jwt.JwtTokenProvider;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.redis.auth.RefreshTokenRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final BCryptPasswordEncoder passwordEncoder;
    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter logoutCounter;

    public AuthService(
            MemberRepository memberRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenProvider jwtTokenProvider,
            BCryptPasswordEncoder passwordEncoder,
            MeterRegistry registry) {
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.loginSuccessCounter = Counter.builder("auth.login")
                .tag("result", "success")
                .description("Successful login count")
                .register(registry);
        this.loginFailureCounter = Counter.builder("auth.login")
                .tag("result", "failure")
                .description("Failed login count")
                .register(registry);
        this.logoutCounter = Counter.builder("auth.logout")
                .description("Logout count")
                .register(registry);
    }

    public record LoginResult(TokenResponse tokenResponse, String refreshToken) {
    }

    public LoginResult login(LoginRequest request) {
        Member member = memberRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> {
                    loginFailureCounter.increment();
                    return new CustomException(ErrorCode.INVALID_CREDENTIALS);
                });

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            loginFailureCounter.increment();
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createAccessToken(member.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId());
        refreshTokenRepository.save(member.getId(), refreshToken);

        loginSuccessCounter.increment();
        return new LoginResult(new TokenResponse(accessToken), refreshToken);
    }

    public LoginResult reissue(String refreshToken) {
        if (!jwtTokenProvider.validate(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        Long memberId = jwtTokenProvider.getMemberId(refreshToken);
        String stored = refreshTokenRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));

        if (stored.equals(refreshToken)) {
            // 정상 회전. 직전 토큰(stored)을 짧은 유예로 남겨, 거의 동시에 온 형제 요청(다중 탭 등)이
            // 그 토큰으로도 통과되게 한다 — 단일 사용 회전이 서로를 무효화하며 로그아웃시키는 걸 막는다.
            String newRefreshToken = jwtTokenProvider.createRefreshToken(memberId);
            refreshTokenRepository.saveGrace(memberId, stored);
            refreshTokenRepository.save(memberId, newRefreshToken);
            return new LoginResult(
                    new TokenResponse(jwtTokenProvider.createAccessToken(memberId)), newRefreshToken);
        }

        // 현재 토큰과 다르면, 방금 회전된 직전 토큰(유예 창 안)인지 본다.
        boolean inGrace = refreshTokenRepository.findGraceByMemberId(memberId)
                .map(refreshToken::equals)
                .orElse(false);
        if (inGrace) {
            // 다른 요청이 이미 회전시킴 → 재회전하지 않고 현재 토큰으로 수렴시킨다(양쪽이 같은 RT를 갖게).
            return new LoginResult(
                    new TokenResponse(jwtTokenProvider.createAccessToken(memberId)), stored);
        }

        // 유효한 서명이지만 현재도 유예도 아님 → 이미 폐기됐거나 재사용(유예 창 밖). 거부.
        throw new CustomException(ErrorCode.INVALID_TOKEN);
    }

    public void logout(String refreshToken) {
        if (jwtTokenProvider.validate(refreshToken)) {
            Long memberId = jwtTokenProvider.getMemberId(refreshToken);
            refreshTokenRepository.deleteByMemberId(memberId);
            logoutCounter.increment();
        }
    }
}
