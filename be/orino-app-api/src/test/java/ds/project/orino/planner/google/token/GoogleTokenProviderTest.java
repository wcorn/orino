package ds.project.orino.planner.google.token;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.google.entity.GoogleAccount;
import ds.project.orino.domain.planner.google.repository.GoogleAccountRepository;
import ds.project.orino.redis.planner.google.GoogleAccessTokenRepository;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.IntegrationTest;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class GoogleTokenProviderTest {

    private static final HttpServer GOOGLE_STUB = createStub();

    @Autowired
    private GoogleTokenProvider tokenProvider;
    @Autowired
    private GoogleAccountRepository accountRepository;
    @Autowired
    private GoogleAccessTokenRepository accessTokenRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private Long memberId;

    @DynamicPropertySource
    static void googleProperties(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + GOOGLE_STUB.getAddress().getPort();
        registry.add("planner.google.client-id", () -> "test-client-id");
        registry.add("planner.google.client-secret", () -> "test-client-secret");
        registry.add("planner.google.oauth.token-uri", () -> base + "/token");
    }

    @AfterAll
    static void stopStub() {
        GOOGLE_STUB.stop(0);
    }

    @BeforeEach
    void setUp() {
        dbCleaner.clean();
        memberId = memberRepository.save(MemberFixture.create()).getId();
    }

    private GoogleAccount saveAccount(String refreshToken) {
        return accountRepository.save(new GoogleAccount(
                memberId, refreshToken, "scope", "me@gmail.com", "primary", "@default"));
    }

    @Test
    @DisplayName("캐시 hit이면 refresh 없이 캐시된 access token을 반환한다")
    void getValidAccessToken_cacheHit() {
        saveAccount("good-refresh");
        accessTokenRepository.save(memberId, "cached-access", Duration.ofMinutes(30));

        assertThat(tokenProvider.getValidAccessToken(memberId)).isEqualTo("cached-access");
    }

    @Test
    @DisplayName("캐시 miss면 refresh grant로 재발급하고 재캐시한다")
    void getValidAccessToken_cacheMiss_refreshes() {
        saveAccount("good-refresh");

        String token = tokenProvider.getValidAccessToken(memberId);

        assertThat(token).isEqualTo("refreshed-access");
        assertThat(accessTokenRepository.findByMemberId(memberId)).contains("refreshed-access");
    }

    @Test
    @DisplayName("연동이 없으면 GOOGLE_NOT_CONNECTED(409)")
    void getValidAccessToken_notConnected() {
        assertThatThrownBy(() -> tokenProvider.getValidAccessToken(memberId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GOOGLE_NOT_CONNECTED);
    }

    @Test
    @DisplayName("revoked 연동이면 GOOGLE_NOT_CONNECTED(409)")
    void getValidAccessToken_revoked() {
        GoogleAccount account = saveAccount("good-refresh");
        account.markRevoked();
        accountRepository.save(account);

        assertThatThrownBy(() -> tokenProvider.getValidAccessToken(memberId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GOOGLE_NOT_CONNECTED);
    }

    @Test
    @DisplayName("refresh가 invalid_grant면 revoked 마킹 후 GOOGLE_INVALID_GRANT(401)")
    void refresh_invalidGrant_marksRevoked() {
        saveAccount("bad-refresh");

        assertThatThrownBy(() -> tokenProvider.getValidAccessToken(memberId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GOOGLE_INVALID_GRANT);

        assertThat(accountRepository.findByMemberId(memberId).orElseThrow().isRevoked()).isTrue();
    }

    @Test
    @DisplayName("executeWithRetry는 401이면 토큰을 강제 갱신 후 1회 재시도한다")
    void executeWithRetry_retriesOnUnauthorized() {
        saveAccount("good-refresh");
        accessTokenRepository.save(memberId, "old-access", Duration.ofMinutes(30));

        AtomicInteger calls = new AtomicInteger();
        List<String> tokensSeen = new ArrayList<>();

        String result = tokenProvider.executeWithRetry(memberId, token -> {
            tokensSeen.add(token);
            if (calls.getAndIncrement() == 0) {
                throw new GoogleUnauthorizedException();
            }
            return token;
        });

        assertThat(result).isEqualTo("refreshed-access");
        assertThat(tokensSeen).containsExactly("old-access", "refreshed-access");
        assertThat(accessTokenRepository.findByMemberId(memberId)).contains("refreshed-access");
    }

    @Test
    @DisplayName("executeWithRetry는 정상 호출이면 재시도하지 않고 결과를 반환한다")
    void executeWithRetry_noRetryOnSuccess() {
        saveAccount("good-refresh");
        accessTokenRepository.save(memberId, "cached-access", Duration.ofMinutes(30));

        AtomicInteger calls = new AtomicInteger();
        String result = tokenProvider.executeWithRetry(memberId, token -> {
            calls.incrementAndGet();
            return "ok:" + token;
        });

        assertThat(result).isEqualTo("ok:cached-access");
        assertThat(calls.get()).isEqualTo(1);
    }

    private static HttpServer createStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/token", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (body.contains("refresh_token=bad-refresh")) {
                    respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
                } else {
                    respond(exchange, 200,
                            "{\"access_token\":\"refreshed-access\",\"expires_in\":3600,\"token_type\":\"Bearer\"}");
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
