package ds.project.orino.planner.google.oauth;

import ds.project.orino.domain.planner.google.entity.GoogleAccount;
import ds.project.orino.domain.planner.google.repository.GoogleAccountRepository;
import ds.project.orino.planner.google.config.GoogleApiProperties;
import ds.project.orino.planner.google.config.GoogleOAuthProperties;
import ds.project.orino.planner.google.oauth.dto.GooglePrimaryCalendar;
import ds.project.orino.planner.google.oauth.dto.GoogleStatusResponse;
import ds.project.orino.planner.google.oauth.dto.GoogleTokenResponse;
import ds.project.orino.redis.planner.google.GoogleAccessTokenRepository;
import ds.project.orino.redis.planner.google.GoogleOAuthStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Google OAuth 연동: 인증 URL 발급, 콜백 토큰 교환, 연동 상태/해제.
 *
 * <p>BE가 refresh token을 독점 보관하고 모든 Google API를 프록시한다(BFF). refresh=DB, access=Redis,
 * state=Redis(5분). 콜백 시점엔 JWT가 없어 url 발급 때 state↔memberId를 Redis에 묶어 콜백에서 복원한다.
 */
@Service
public class GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

    /** access token 만료 직전 갱신을 위한 캐시 TTL 여유분. */
    private static final long TTL_SKEW_SECONDS = 60;
    /** Google Tasks 기본 task list 별칭. 실제 id 해석은 Tasks 프록시(M3)에서 필요 시 보강. */
    private static final String DEFAULT_TASK_LIST = "@default";
    /** primary 캘린더 별칭(events.list 등에 그대로 사용 가능). */
    private static final String PRIMARY_CALENDAR = "primary";

    private final GoogleApiProperties apiProperties;
    private final GoogleOAuthProperties oauthProperties;
    private final RestClient googleRestClient;
    private final GoogleAccountRepository accountRepository;
    private final GoogleAccessTokenRepository accessTokenRepository;
    private final GoogleOAuthStateRepository oauthStateRepository;

    public GoogleOAuthService(GoogleApiProperties apiProperties,
                              GoogleOAuthProperties oauthProperties,
                              RestClient googleRestClient,
                              GoogleAccountRepository accountRepository,
                              GoogleAccessTokenRepository accessTokenRepository,
                              GoogleOAuthStateRepository oauthStateRepository) {
        this.apiProperties = apiProperties;
        this.oauthProperties = oauthProperties;
        this.googleRestClient = googleRestClient;
        this.accountRepository = accountRepository;
        this.accessTokenRepository = accessTokenRepository;
        this.oauthStateRepository = oauthStateRepository;
    }

    /** 동의 화면 인증 URL 발급. state(memberId 바인딩)를 Redis(5분)에 저장한다. */
    public String createAuthorizationUrl(Long memberId) {
        String state = UUID.randomUUID().toString();
        oauthStateRepository.save(state, memberId);

        return UriComponentsBuilder.fromUriString(oauthProperties.authorizationUri())
                .queryParam("client_id", apiProperties.clientId())
                .queryParam("redirect_uri", apiProperties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(" ", apiProperties.scopes()))
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    /**
     * OAuth 콜백 처리. state 검증 → code 교환 → refresh DB 저장 + access Redis 캐시.
     * 성공/실패에 따라 FE 리다이렉트 URL을 반환한다(예외를 던지지 않는다 — 브라우저 redirect).
     */
    @Transactional
    public String handleCallback(String code, String state, String error) {
        Optional<Long> memberIdOpt = (state == null) ? Optional.empty() : oauthStateRepository.findMemberId(state);
        if (state != null) {
            oauthStateRepository.delete(state); // 1회성 소비
        }
        if (memberIdOpt.isEmpty()) {
            log.warn("Google OAuth callback: invalid/expired state");
            return redirect("error");
        }
        if (error != null || code == null) {
            log.warn("Google OAuth callback: denied or missing code (error={})", error);
            return redirect("error");
        }

        Long memberId = memberIdOpt.get();
        try {
            GoogleTokenResponse token = exchangeCodeForToken(code);
            String email = fetchPrimaryCalendarEmail(token.accessToken());
            upsertAccount(memberId, token, email);
            cacheAccessToken(memberId, token);
            return redirect("connected");
        } catch (RuntimeException e) {
            log.warn("Google OAuth callback failed for member {}: {}", memberId, e.getMessage());
            return redirect("error");
        }
    }

    /** 연동 상태 조회. 미연동 또는 revoked면 connected=false. */
    @Transactional(readOnly = true)
    public GoogleStatusResponse getStatus(Long memberId) {
        return accountRepository.findByMemberId(memberId)
                .filter(account -> !account.isRevoked())
                .map(account -> GoogleStatusResponse.connected(
                        account.getGoogleEmail(),
                        splitScopes(account.getScopes()),
                        account.getConnectedAt(),
                        account.isReviewMirrorEnabled()))
                .orElseGet(GoogleStatusResponse::disconnected);
    }

    /** 연동 해제: Google revoke(실패해도 진행) + DB row 삭제 + Redis access 삭제. 멱등. */
    @Transactional
    public void disconnect(Long memberId) {
        accountRepository.findByMemberId(memberId).ifPresent(account -> {
            revokeQuietly(account.getRefreshToken());
            accountRepository.deleteByMemberId(memberId);
        });
        accessTokenRepository.deleteByMemberId(memberId);
    }

    private GoogleTokenResponse exchangeCodeForToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", apiProperties.clientId());
        form.add("client_secret", apiProperties.clientSecret());
        form.add("redirect_uri", apiProperties.redirectUri());
        form.add("grant_type", "authorization_code");

        return googleRestClient.post()
                .uri(oauthProperties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(GoogleTokenResponse.class);
    }

    /** primary 캘린더 id(=계정 이메일) 조회. 실패해도 연동은 진행(null 반환). */
    private String fetchPrimaryCalendarEmail(String accessToken) {
        try {
            GooglePrimaryCalendar calendar = googleRestClient.get()
                    .uri(oauthProperties.calendarApiBaseUrl() + "/calendar/v3/calendars/primary")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(GooglePrimaryCalendar.class);
            return calendar != null ? calendar.id() : null;
        } catch (RuntimeException e) {
            log.warn("primary 캘린더 조회 실패(연동은 계속): {}", e.getMessage());
            return null;
        }
    }

    private void upsertAccount(Long memberId, GoogleTokenResponse token, String email) {
        Optional<GoogleAccount> existing = accountRepository.findByMemberId(memberId);
        if (existing.isPresent()) {
            GoogleAccount account = existing.get();
            // 재동의 시 refresh_token이 비어 오면 기존 값을 유지한다.
            String refreshToken = token.refreshToken() != null ? token.refreshToken() : account.getRefreshToken();
            account.reconnect(refreshToken, token.scope(), email, PRIMARY_CALENDAR, DEFAULT_TASK_LIST);
        } else {
            accountRepository.save(new GoogleAccount(
                    memberId, token.refreshToken(), token.scope(), email, PRIMARY_CALENDAR, DEFAULT_TASK_LIST));
        }
    }

    private void cacheAccessToken(Long memberId, GoogleTokenResponse token) {
        if (token.accessToken() == null || token.expiresIn() == null) {
            return;
        }
        long ttl = Math.max(TTL_SKEW_SECONDS, token.expiresIn() - TTL_SKEW_SECONDS);
        accessTokenRepository.save(memberId, token.accessToken(), Duration.ofSeconds(ttl));
    }

    private void revokeQuietly(String refreshToken) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("token", refreshToken);
            googleRestClient.post()
                    .uri(oauthProperties.revokeUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("Google revoke 실패(로컬 정리는 계속): {}", e.getMessage());
        }
    }

    private String redirect(String result) {
        return UriComponentsBuilder.fromUriString(oauthProperties.frontendUrl())
                .path("/integrations")
                .queryParam("google", result)
                .build()
                .toUriString();
    }

    private List<String> splitScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return List.of();
        }
        return List.of(scopes.trim().split("\\s+"));
    }
}
