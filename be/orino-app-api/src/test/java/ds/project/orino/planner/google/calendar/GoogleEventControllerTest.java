package ds.project.orino.planner.google.calendar;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.google.entity.GoogleAccount;
import ds.project.orino.domain.planner.google.repository.GoogleAccountRepository;
import ds.project.orino.redis.planner.google.GoogleAccessTokenRepository;
import ds.project.orino.redis.planner.google.GoogleCalendarCacheRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoogleEventControllerTest extends ApiTestSupport {

    private static final HttpServer EVENTS_STUB = createStub();
    private static final String EVENT_JSON = """
            {"id":"g-evt-1","summary":"치과 예약","location":"강남",
             "start":{"dateTime":"2026-06-10T05:00:00Z"},"end":{"dateTime":"2026-06-10T06:00:00Z"}}""";

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private GoogleAccountRepository googleAccountRepository;
    @Autowired
    private GoogleAccessTokenRepository accessTokenRepository;
    @Autowired
    private GoogleCalendarCacheRepository cacheRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private Long memberId;
    private String authHeader;

    @DynamicPropertySource
    static void googleProperties(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + EVENTS_STUB.getAddress().getPort();
        registry.add("planner.google.client-id", () -> "test-client-id");
        registry.add("planner.google.client-secret", () -> "test-client-secret");
        registry.add("planner.google.oauth.calendar-api-base-url", () -> base);
    }

    @AfterAll
    static void stopStub() {
        EVENTS_STUB.stop(0);
    }

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberId = memberRepository.save(MemberFixture.create()).getId();
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    private void connectGoogle() {
        googleAccountRepository.save(new GoogleAccount(
                memberId, "refresh", "scope", "me@gmail.com", "primary", "@default"));
        accessTokenRepository.save(memberId, "test-access", Duration.ofMinutes(30));
    }

    private static final String CREATE_BODY = """
            {"title":"치과 예약","allDay":false,
             "start":"2026-06-10T14:00:00","end":"2026-06-10T15:00:00","location":"강남"}""";

    @Test
    @DisplayName("POST - 일정을 생성하고 201로 정규화된 이벤트를 반환하며 캐시를 무효화한다")
    void create() throws Exception {
        connectGoogle();
        cacheRepository.save(memberId, "2026-06-01", "2026-06-30", "[]", Duration.ofMinutes(1));

        mockMvc.perform(post("/api/planner/calendar/events")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("g-evt-1"))
                .andExpect(jsonPath("$.data.title").value("치과 예약"))
                .andExpect(jsonPath("$.data.allDay").value(false))
                .andExpect(jsonPath("$.data.start").value("2026-06-10T14:00:00"))
                .andExpect(jsonPath("$.data.source").value("google"));

        assertThat(cacheRepository.find(memberId, "2026-06-01", "2026-06-30")).isEmpty();
    }

    @Test
    @DisplayName("PATCH - 일정을 수정하고 200으로 반환한다")
    void update() throws Exception {
        connectGoogle();

        mockMvc.perform(patch("/api/planner/calendar/events/g-evt-1")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("g-evt-1"))
                .andExpect(jsonPath("$.data.title").value("치과 예약"));
    }

    @Test
    @DisplayName("DELETE - 일정을 삭제하고 200 success를 반환한다")
    void deleteEvent() throws Exception {
        connectGoogle();

        mockMvc.perform(delete("/api/planner/calendar/events/g-evt-1")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("요청에 성공하였습니다."));
    }

    @Test
    @DisplayName("미연동 상태에서 생성하면 409(PLN-ERR-003)")
    void create_notConnected() throws Exception {
        mockMvc.perform(post("/api/planner/calendar/events")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLN-ERR-003"));
    }

    @Test
    @DisplayName("title이 없으면 400")
    void create_validation() throws Exception {
        connectGoogle();

        mockMvc.perform(post("/api/planner/calendar/events")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"allDay":false,"start":"2026-06-10T14:00:00","end":"2026-06-10T15:00:00"}"""))
                .andExpect(status().isBadRequest());
    }

    private static HttpServer createStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/calendar/v3/calendars/primary/events", exchange -> {
                if ("DELETE".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                    return;
                }
                respond(exchange, 200, EVENT_JSON);
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
