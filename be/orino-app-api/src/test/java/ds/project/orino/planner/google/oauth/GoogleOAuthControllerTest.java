package ds.project.orino.planner.google.oauth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.google.entity.GoogleAccount;
import ds.project.orino.domain.planner.google.repository.GoogleAccountRepository;
import ds.project.orino.redis.planner.google.GoogleAccessTokenRepository;
import ds.project.orino.redis.planner.google.GoogleOAuthStateRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoogleOAuthControllerTest extends ApiTestSupport {

    private static final HttpServer GOOGLE_STUB = createStub();

    @Autowired
    private GoogleAccountRepository accountRepository;
    @Autowired
    private GoogleOAuthStateRepository oauthStateRepository;
    @Autowired
    private GoogleAccessTokenRepository accessTokenRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private Long memberId;
    private String authHeader;

    @DynamicPropertySource
    static void googleProperties(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + GOOGLE_STUB.getAddress().getPort();
        registry.add("planner.google.client-id", () -> "test-client-id");
        registry.add("planner.google.client-secret", () -> "test-client-secret");
        registry.add("planner.google.redirect-uri",
                () -> "http://localhost:8080/api/integrations/google/oauth/callback");
        registry.add("planner.google.oauth.token-uri", () -> base + "/token");
        registry.add("planner.google.oauth.revoke-uri", () -> base + "/revoke");
        registry.add("planner.google.oauth.calendar-api-base-url", () -> base);
        registry.add("planner.google.oauth.frontend-url", () -> "http://localhost:3000");
    }

    @AfterAll
    static void stopStub() {
        GOOGLE_STUB.stop(0);
    }

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberId = memberRepository.save(MemberFixture.create()).getId();
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    @Test
    @DisplayName("GET /oauth/url - 인증 URL을 발급하고 state를 Redis에 저장한다")
    void authorizationUrl() throws Exception {
        String url = mockMvc.perform(get("/api/integrations/google/oauth/url")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorizationUrl").exists())
                .andReturn().getResponse().getContentAsString();

        String authorizationUrl = com.jayway.jsonpath.JsonPath.read(url, "$.data.authorizationUrl");
        assertThat(authorizationUrl)
                .contains("client_id=test-client-id")
                .contains("access_type=offline")
                .contains("prompt=consent")
                .contains("response_type=code");

        String state = UriComponentsBuilder.fromUriString(authorizationUrl)
                .build().getQueryParams().getFirst("state");
        assertThat(state).isNotBlank();
        assertThat(oauthStateRepository.findMemberId(state)).contains(memberId);
    }

    @Test
    @DisplayName("GET /oauth/url - 인증 없이 호출하면 401")
    void authorizationUrl_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/integrations/google/oauth/url"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /oauth/callback - 유효한 state면 토큰 교환 후 연동하고 connected로 리다이렉트한다")
    void callback_validState() throws Exception {
        oauthStateRepository.save("state-ok", memberId);

        mockMvc.perform(get("/api/integrations/google/oauth/callback")
                        .param("code", "auth-code")
                        .param("state", "state-ok"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:3000/integrations?google=connected"));

        GoogleAccount account = accountRepository.findByMemberId(memberId).orElseThrow();
        assertThat(account.getRefreshToken()).isEqualTo("stub-refresh");
        assertThat(account.getGoogleEmail()).isEqualTo("me@gmail.com");
        assertThat(account.getScopes()).contains("calendar");
        assertThat(account.isRevoked()).isFalse();
        assertThat(accessTokenRepository.findByMemberId(memberId)).contains("stub-access");
        // state는 1회성 소비
        assertThat(oauthStateRepository.findMemberId("state-ok")).isEmpty();
    }

    @Test
    @DisplayName("GET /oauth/callback - 유효하지 않은 state면 error로 리다이렉트한다")
    void callback_invalidState() throws Exception {
        mockMvc.perform(get("/api/integrations/google/oauth/callback")
                        .param("code", "auth-code")
                        .param("state", "unknown-state"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:3000/integrations?google=error"));

        assertThat(accountRepository.findByMemberId(memberId)).isEmpty();
    }

    @Test
    @DisplayName("GET /oauth/callback - 토큰 교환 실패면 error로 리다이렉트한다")
    void callback_tokenExchangeFails() throws Exception {
        oauthStateRepository.save("state-bad", memberId);

        mockMvc.perform(get("/api/integrations/google/oauth/callback")
                        .param("code", "bad-code")
                        .param("state", "state-bad"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:3000/integrations?google=error"));

        assertThat(accountRepository.findByMemberId(memberId)).isEmpty();
    }

    @Test
    @DisplayName("GET /status - 연동된 경우 connected=true와 상세를 반환한다")
    void status_connected() throws Exception {
        accountRepository.save(new GoogleAccount(
                memberId, "refresh", "https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/tasks",
                "me@gmail.com", "primary", "@default"));

        mockMvc.perform(get("/api/integrations/google/status")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.googleEmail").value("me@gmail.com"))
                .andExpect(jsonPath("$.data.scopes.length()").value(2))
                .andExpect(jsonPath("$.data.connectedAt").exists())
                .andExpect(jsonPath("$.data.reviewMirrorEnabled").value(false));
    }

    @Test
    @DisplayName("GET /status - 복습 미러가 켜져 있으면 reviewMirrorEnabled=true")
    void status_reviewMirrorEnabled() throws Exception {
        GoogleAccount account = new GoogleAccount(
                memberId, "refresh", "scope", "me@gmail.com", "primary", "@default");
        account.enableReviewMirror("c_review@group.calendar.google.com");
        accountRepository.save(account);

        mockMvc.perform(get("/api/integrations/google/status")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.reviewMirrorEnabled").value(true));
    }

    @Test
    @DisplayName("GET /status - 미연동이면 connected=false")
    void status_notConnected() throws Exception {
        mockMvc.perform(get("/api/integrations/google/status")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false))
                .andExpect(jsonPath("$.data.googleEmail").doesNotExist());
    }

    @Test
    @DisplayName("POST /disconnect - 연동을 해제하고 DB row와 access 캐시를 삭제한다")
    void disconnect() throws Exception {
        accountRepository.save(new GoogleAccount(memberId, "refresh", "scope", "me@gmail.com", "primary", "@default"));
        accessTokenRepository.save(memberId, "cached-access", java.time.Duration.ofMinutes(30));

        mockMvc.perform(post("/api/integrations/google/disconnect")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("요청에 성공하였습니다."));

        assertThat(accountRepository.findByMemberId(memberId)).isEmpty();
        assertThat(accessTokenRepository.findByMemberId(memberId)).isEmpty();
    }

    private static HttpServer createStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/token", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (body.contains("code=bad-code")) {
                    respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
                } else {
                    respond(exchange, 200, """
                            {"access_token":"stub-access","refresh_token":"stub-refresh","expires_in":3600,\
                            "scope":"https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/tasks",\
                            "token_type":"Bearer"}""");
                }
            });
            server.createContext("/calendar/v3/calendars/primary", exchange ->
                    respond(exchange, 200, "{\"id\":\"me@gmail.com\",\"summary\":\"me@gmail.com\"}"));
            server.createContext("/revoke", exchange -> respond(exchange, 200, ""));
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 0) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
        }
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } else {
            exchange.close();
        }
    }
}
