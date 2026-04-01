package ds.project.orino.domain.auth.service;

import ds.project.orino.common.response.exception.CustomException;
import ds.project.orino.common.response.exception.ErrorCode;
import ds.project.orino.config.jwt.JwtTokenProvider;
import ds.project.orino.domain.auth.dto.LoginRequest;
import ds.project.orino.domain.auth.dto.TokenResponse;
import ds.project.orino.domain.auth.repository.RefreshTokenRepository;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(MemberRepository memberRepository, RefreshTokenRepository refreshTokenRepository,
                       JwtTokenProvider jwtTokenProvider, BCryptPasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    public record LoginResult(TokenResponse tokenResponse, String refreshToken) {
    }

    public LoginResult login(LoginRequest request) {
        Member member = memberRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createAccessToken(member.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId());
        refreshTokenRepository.save(member.getId(), refreshToken);

        return new LoginResult(new TokenResponse(accessToken), refreshToken);
    }

    public LoginResult reissue(String refreshToken) {
        if (!jwtTokenProvider.validate(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        Long memberId = jwtTokenProvider.getMemberId(refreshToken);
        String stored = refreshTokenRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));

        if (!stored.equals(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(memberId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(memberId);
        refreshTokenRepository.save(memberId, newRefreshToken);

        return new LoginResult(new TokenResponse(newAccessToken), newRefreshToken);
    }

    public void logout(String refreshToken) {
        if (jwtTokenProvider.validate(refreshToken)) {
            Long memberId = jwtTokenProvider.getMemberId(refreshToken);
            refreshTokenRepository.deleteByMemberId(memberId);
        }
    }
}
