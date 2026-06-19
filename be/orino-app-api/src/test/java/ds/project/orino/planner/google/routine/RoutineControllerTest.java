package ds.project.orino.planner.google.routine;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoutineControllerTest extends ApiTestSupport {

    /** POST = 생성된 마스터(habit, 종일, RRULE+태그). */
    private static final String MASTER_JSON = """
            {"id":"r-habit-1","summary":"운동하기",
             "start":{"date":"2026-06-20"},"end":{"date":"2026-06-21"},
             "recurrence":["RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR"],
             "extendedProperties":{"private":{"orinoRoutine":"1","orinoRoutineType":"habit"}}}""";
    /** GET = singleEvents 펼침 인스턴스(recurringEventId + 태그 상속). */
    private static final String FEED_JSON = """
            {"items":[{"id":"r-habit-1_20260622","summary":"운동하기","recurringEventId":"r-habit-1",
             "start":{"date":"2026-06-22"},"end":{"date":"2026-06-23"},
             "extendedProperties":{"private":{"orinoRoutine":"1","orinoRoutineType":"habit"}}}]}""";

    private static final HttpServer EVENTS_STUB = createStub();

    private static final String HABIT_BODY = """
            {"type":"habit","title":"운동하기","allDay":true,
             "start":"2026-06-20","end":"2026-06-20",
             "recurrence":{"freq":"WEEKLY","byDay":["MO","WE","FR"]}}""";

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
        registry.add("planner.google.oauth.tasks-api-base-url", () -> base);
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

    @Test
    @DisplayName("POST - 루틴을 생성하고 201 시리즈 요약을 반환하며 캐시를 무효화한다")
    void create() throws Exception {
        connectGoogle();
        cacheRepository.save(memberId, "2026-06-01", "2026-06-30", "[]", Duration.ofMinutes(1));

        mockMvc.perform(post("/api/planner/routines")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(HABIT_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recurringEventId").value("r-habit-1"))
                .andExpect(jsonPath("$.data.type").value("habit"))
                .andExpect(jsonPath("$.data.title").value("운동하기"))
                .andExpect(jsonPath("$.data.allDay").value(true))
                .andExpect(jsonPath("$.data.start").value("2026-06-20"))
                .andExpect(jsonPath("$.data.recurrence.freq").value("WEEKLY"))
                .andExpect(jsonPath("$.data.recurrence.byDay[0]").value("MO"))
                .andExpect(jsonPath("$.data.recurrenceText").value("매주 월·수·금"));

        assertThat(cacheRepository.find(memberId, "2026-06-01", "2026-06-30")).isEmpty();
    }

    @Test
    @DisplayName("생성된 루틴 인스턴스는 통합 피드에 routine 메타로 노출된다")
    void feedAnnotatesRoutine() throws Exception {
        connectGoogle();

        mockMvc.perform(get("/api/planner/calendar")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events.length()").value(1))
                .andExpect(jsonPath("$.data.events[0].recurring").value(true))
                .andExpect(jsonPath("$.data.events[0].routine.type").value("habit"))
                .andExpect(jsonPath("$.data.events[0].routine.recurringEventId").value("r-habit-1"))
                .andExpect(jsonPath("$.data.events[0].routine.done").value(false));
    }

    @Test
    @DisplayName("미연동 상태에서 생성하면 409(PLN-ERR-003)")
    void create_notConnected() throws Exception {
        mockMvc.perform(post("/api/planner/routines")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(HABIT_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLN-ERR-003"));
    }

    @Test
    @DisplayName("알 수 없는 freq면 400(PLN-ERR-002)")
    void create_invalidRecurrence() throws Exception {
        connectGoogle();
        String body = """
                {"type":"habit","title":"운동하기","allDay":true,
                 "start":"2026-06-20","end":"2026-06-20",
                 "recurrence":{"freq":"YEARLY"}}""";

        mockMvc.perform(post("/api/planner/routines")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLN-ERR-002"));
    }

    @Test
    @DisplayName("title이 없으면 400")
    void create_validation() throws Exception {
        connectGoogle();
        String body = """
                {"type":"habit","allDay":true,"start":"2026-06-20","end":"2026-06-20",
                 "recurrence":{"freq":"DAILY"}}""";

        mockMvc.perform(post("/api/planner/routines")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("recurrence가 없으면 400")
    void create_missingRecurrence() throws Exception {
        connectGoogle();
        String body = """
                {"type":"habit","title":"운동하기","allDay":true,
                 "start":"2026-06-20","end":"2026-06-20"}""";

        mockMvc.perform(post("/api/planner/routines")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private static HttpServer createStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/calendar/v3/calendars/primary/events", exchange -> {
                String body = "POST".equals(exchange.getRequestMethod()) ? MASTER_JSON : FEED_JSON;
                respond(exchange, 200, body);
            });
            server.createContext("/tasks/v1/lists/@default/tasks", exchange ->
                    respond(exchange, 200, "{\"items\":[]}"));
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
