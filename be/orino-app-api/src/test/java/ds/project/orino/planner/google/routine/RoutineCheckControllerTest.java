package ds.project.orino.planner.google.routine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.google.entity.GoogleAccount;
import ds.project.orino.domain.planner.google.repository.GoogleAccountRepository;
import ds.project.orino.domain.planner.routine.entity.RoutineCheck;
import ds.project.orino.domain.planner.routine.repository.RoutineCheckRepository;
import ds.project.orino.redis.planner.google.GoogleAccessTokenRepository;
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
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoutineCheckControllerTest extends ApiTestSupport {

    private static final String HABIT_MASTER = """
            {"id":"r-habit-1","summary":"운동하기","start":{"date":"2026-06-20"},"end":{"date":"2026-06-21"},
             "recurrence":["RRULE:FREQ=DAILY"],
             "extendedProperties":{"private":{"orinoRoutine":"1","orinoRoutineType":"habit"}}}""";
    private static final String SCHEDULE_MASTER = """
            {"id":"r-sched-1","summary":"스탠드업",
             "start":{"dateTime":"2026-06-20T00:00:00Z"},"end":{"dateTime":"2026-06-20T00:15:00Z"},
             "recurrence":["RRULE:FREQ=DAILY"],
             "extendedProperties":{"private":{"orinoRoutine":"1","orinoRoutineType":"schedule"}}}""";
    /** 피드 GET: 2026-06-20 habit 인스턴스 1건. */
    private static final String FEED_JSON = """
            {"items":[{"id":"r-habit-1_20260620","summary":"운동하기","recurringEventId":"r-habit-1",
             "start":{"date":"2026-06-20"},"end":{"date":"2026-06-21"},
             "extendedProperties":{"private":{"orinoRoutine":"1","orinoRoutineType":"habit"}}}]}""";

    private static final HttpServer EVENTS_STUB = createStub();

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private GoogleAccountRepository googleAccountRepository;
    @Autowired
    private GoogleAccessTokenRepository accessTokenRepository;
    @Autowired
    private RoutineCheckRepository routineCheckRepository;
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

    private org.springframework.test.web.servlet.ResultActions check(String id, String body) throws Exception {
        return mockMvc.perform(post("/api/planner/routines/{id}/check", id)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions feed() throws Exception {
        return mockMvc.perform(get("/api/planner/calendar")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .param("from", "2026-06-01")
                .param("to", "2026-06-30"));
    }

    @Test
    @DisplayName("done=true 체크 후 통합 피드 인스턴스에 done=true가 반영된다")
    void check_thenFeedShowsDone() throws Exception {
        connectGoogle();

        check("r-habit-1", "{\"date\":\"2026-06-20\",\"done\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recurringEventId").value("r-habit-1"))
                .andExpect(jsonPath("$.data.date").value("2026-06-20"))
                .andExpect(jsonPath("$.data.done").value(true));

        assertThat(routineCheckRepository.existsByMemberIdAndRecurringEventIdAndInstanceDate(
                memberId, "r-habit-1", LocalDate.of(2026, 6, 20))).isTrue();

        feed()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[0].routine.recurringEventId").value("r-habit-1"))
                .andExpect(jsonPath("$.data.events[0].routine.done").value(true));
    }

    @Test
    @DisplayName("done=false 해제 시 행이 삭제되고 피드 done=false")
    void uncheck_removesRowAndFeedDoneFalse() throws Exception {
        connectGoogle();
        routineCheckRepository.save(new RoutineCheck(memberId, "r-habit-1", LocalDate.of(2026, 6, 20)));

        check("r-habit-1", "{\"date\":\"2026-06-20\",\"done\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.done").value(false));

        assertThat(routineCheckRepository.existsByMemberIdAndRecurringEventIdAndInstanceDate(
                memberId, "r-habit-1", LocalDate.of(2026, 6, 20))).isFalse();

        feed()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[0].routine.done").value(false));
    }

    @Test
    @DisplayName("schedule 루틴에 체크하면 400(체크 비대상)")
    void check_schedule_returns400() throws Exception {
        connectGoogle();

        check("r-sched-1", "{\"date\":\"2026-06-20\",\"done\":true}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("미연동 상태면 409(PLN-ERR-003)")
    void check_notConnected_409() throws Exception {
        check("r-habit-1", "{\"date\":\"2026-06-20\",\"done\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLN-ERR-003"));
    }

    @Test
    @DisplayName("존재하지 않는 시리즈면 404")
    void check_missingSeries_404() throws Exception {
        connectGoogle();

        check("r-missing", "{\"date\":\"2026-06-20\",\"done\":true}")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("date가 없으면 400")
    void check_validation() throws Exception {
        connectGoogle();

        check("r-habit-1", "{\"done\":true}")
                .andExpect(status().isBadRequest());
    }

    private static HttpServer createStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/calendar/v3/calendars/primary/events", RoutineCheckControllerTest::handleEvents);
            server.createContext("/tasks/v1/lists/@default/tasks", exchange ->
                    respond(exchange, 200, "{\"items\":[]}"));
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** base 경로(GET)=피드 인스턴스, .../events/{id}(GET)=마스터 단건(getEvent), 모르는 id=404. */
    private static void handleEvents(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String suffix = path.substring(path.indexOf("/events") + "/events".length());
        if (suffix.isEmpty() || suffix.equals("/")) {
            respond(exchange, 200, FEED_JSON);
            return;
        }
        String id = suffix.startsWith("/") ? suffix.substring(1) : suffix;
        switch (id) {
            case "r-habit-1" -> respond(exchange, 200, HABIT_MASTER);
            case "r-sched-1" -> respond(exchange, 200, SCHEDULE_MASTER);
            default -> respond(exchange, 404, "{\"error\":\"notFound\"}");
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
