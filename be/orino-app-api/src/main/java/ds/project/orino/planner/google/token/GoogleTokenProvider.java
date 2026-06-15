package ds.project.orino.planner.google.token;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.google.entity.GoogleAccount;
import ds.project.orino.domain.planner.google.repository.GoogleAccountRepository;
import ds.project.orino.planner.google.config.GoogleApiProperties;
import ds.project.orino.planner.google.config.GoogleOAuthProperties;
import ds.project.orino.planner.google.oauth.dto.GoogleTokenResponse;
import ds.project.orino.redis.planner.google.GoogleAccessTokenRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.function.Function;

/**
 * Google access token 공급자. Redis 캐시 우선, miss 시 refresh grant로 재발급·재캐시한다.
 *
 * <p>refresh가 {@code invalid_grant}면 {@link GoogleAccount#markRevoked()} 후 재연동 예외(PLN-ERR-005)를 던지고,
 * 연동이 없거나 revoked면 PLN-ERR-003을 던진다. access token 만료로 API가 401을 주면
 * {@link #executeWithRetry}가 1회 강제 갱신 후 재시도한다.
 *
 * <p>트랜잭션 self-invocation 함정을 피하려 revoked 마킹은 dirty-checking 대신 명시적 save로 영속화한다.
 */
@Component
public class GoogleTokenProvider {

    /** access token 만료 직전 갱신을 위한 캐시 TTL 여유분. */
    private static final long TTL_SKEW_SECONDS = 60;

    private final GoogleAccountRepository accountRepository;
    private final GoogleAccessTokenRepository accessTokenRepository;
    private final RestClient googleRestClient;
    private final GoogleApiProperties apiProperties;
    private final GoogleOAuthProperties oauthProperties;

    public GoogleTokenProvider(GoogleAccountRepository accountRepository,
                               GoogleAccessTokenRepository accessTokenRepository,
                               RestClient googleRestClient,
                               GoogleApiProperties apiProperties,
                               GoogleOAuthProperties oauthProperties) {
        this.accountRepository = accountRepository;
        this.accessTokenRepository = accessTokenRepository;
        this.googleRestClient = googleRestClient;
        this.apiProperties = apiProperties;
        this.oauthProperties = oauthProperties;
    }

    /** 유효한 access token 반환. 캐시 hit→사용 / miss→refresh grant 재발급·재캐시. */
    public String getValidAccessToken(Long memberId) {
        return accessTokenRepository.findByMemberId(memberId)
                .orElseGet(() -> refreshAndCache(memberId));
    }

    /**
     * access token으로 Google API를 호출하되, 401({@link GoogleUnauthorizedException})이면 토큰을
     * 1회 강제 갱신한 뒤 재시도한다.
     */
    public <T> T executeWithRetry(Long memberId, Function<String, T> apiCall) {
        String accessToken = getValidAccessToken(memberId);
        try {
            return apiCall.apply(accessToken);
        } catch (GoogleUnauthorizedException e) {
            return apiCall.apply(forceRefresh(memberId));
        }
    }

    private String forceRefresh(Long memberId) {
        accessTokenRepository.deleteByMemberId(memberId);
        return refreshAndCache(memberId);
    }

    private String refreshAndCache(Long memberId) {
        GoogleAccount account = accountRepository.findByMemberId(memberId)
                .filter(a -> !a.isRevoked())
                .orElseThrow(() -> new CustomException(ErrorCode.GOOGLE_NOT_CONNECTED));

        GoogleTokenResponse token = requestRefreshGrant(account);
        cacheAccessToken(memberId, token);
        return token.accessToken();
    }

    private GoogleTokenResponse requestRefreshGrant(GoogleAccount account) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", account.getRefreshToken());
        form.add("client_id", apiProperties.clientId());
        form.add("client_secret", apiProperties.clientSecret());

        try {
            return googleRestClient.post()
                    .uri(oauthProperties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
        } catch (CustomException e) {
            if (e.getErrorCode() == ErrorCode.GOOGLE_INVALID_GRANT) {
                account.markRevoked();
                accountRepository.save(account);
            }
            throw e;
        }
    }

    private void cacheAccessToken(Long memberId, GoogleTokenResponse token) {
        if (token == null || token.accessToken() == null) {
            throw new CustomException(ErrorCode.GOOGLE_API_FAILED);
        }
        long expiresIn = token.expiresIn() != null ? token.expiresIn() : TTL_SKEW_SECONDS * 2;
        long ttl = Math.max(TTL_SKEW_SECONDS, expiresIn - TTL_SKEW_SECONDS);
        accessTokenRepository.save(memberId, token.accessToken(), Duration.ofSeconds(ttl));
    }
}
