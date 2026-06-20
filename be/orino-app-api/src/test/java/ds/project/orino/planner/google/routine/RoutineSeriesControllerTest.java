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
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoutineSeriesControllerTest extends ApiTestSupport {

    private static final String EMPTY_ITEMS = "{\"items\":[]}";
    private static final String HABIT_MASTER = """
            {"id":"r-habit-1","summary":"운동하기","start":{"date":"2026-06-20"},"end":{"date":"2026-06-21"},
             "recurrence":["RRULE:FREQ=DAILY"],
             "extendedProperties":{"private":{"orinoRoutine":"1","orinoRoutineType":"habit"}}}""";
    /** PATCH 응답(편집 반영된 마스터/인스턴스). */
    private static final String EDITED = """
            {"id":"r-habit-1","summary":"운동하기(아침)","start":{"date":"2026-06-20"},"end":{"date":"2026-06-21"},
             "recurrence":["RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR"],
             "extendedProperties":{"private":{"orinoRoutine":"1","orinoRoutineType":"habit"}}}""";
    /** POST 응답(following 분할로 생성된 새 시리즈, 새 id). */
    private static final String FORKED = """
            {"id":"r-new-1","summary":"운동하기(아침)","start":{"date":"2026-06-20"},"end":{"date":"2026-06-21"},
             "recurrence":["RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR"],
             "extendedProperties":{"private":{"orinoRoutine":"1","orinoRoutineType":"habit",
               "orinoRoutineSplitOf":"r-habit-1@2026-06-20"}}}""";
    /** GET .../instances 응답. */
    private static final String INSTANCES = """
            {"items":[{"id":"r-habit-1_20260620","summary":"운동하기","recurringEventId":"r-habit-1",
             "start":{"date":"2026-06-20"},"end":{"date":"2026-06-21"},
             "extendedProperties":{"private":{"orinoRoutine":"1","orinoRoutineType":"habit"}}}]}""";

    private static final String EDIT_BODY = """
            {"title":"운동하기(아침)","allDay":true,"start":"2026-06-20","end":"2026-06-20",
             "recurrence":{"freq":"WEEKLY","byDay":["MO","WE","FR"]}}""";

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

    private void seedCheck(String recurringEventId, LocalDate date) {
        routineCheckRepository.save(new RoutineCheck(memberId, recurringEventId, date));
    }

    private boolean hasCheck(String recurringEventId, LocalDate date) {
        return routineCheckRepository.existsByMemberIdAndRecurringEventIdAndInstanceDate(
                memberId, recurringEventId, date);
    }

    private ResultActions editRoutine(String scope, String instanceDate) throws Exception {
        var req = patch("/api/planner/routines/{id}", "r-habit-1")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(EDIT_BODY)
                .param("scope", scope);
        if (instanceDate != null) {
            req = req.param("instanceDate", instanceDate);
        }
        return mockMvc.perform(req);
    }

    private ResultActions deleteRoutine(String scope, String instanceDate) throws Exception {
        var req = delete("/api/planner/routines/{id}", "r-habit-1")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .param("scope", scope);
        if (instanceDate != null) {
            req = req.param("instanceDate", instanceDate);
        }
        return mockMvc.perform(req);
    }

    // ---------- EDIT ----------

    @Test
    @DisplayName("scope=all 편집 - 마스터를 수정하고 같은 시리즈 id로 요약을 반환한다")
    void editAll() throws Exception {
        connectGoogle();

        editRoutine("all", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recurringEventId").value("r-habit-1"))
                .andExpect(jsonPath("$.data.type").value("habit"))
                .andExpect(jsonPath("$.data.title").value("운동하기(아침)"))
                .andExpect(jsonPath("$.data.recurrence.freq").value("WEEKLY"))
                .andExpect(jsonPath("$.data.recurrenceText").value("매주 월·수·금"));
    }

    @Test
    @DisplayName("scope=instance 편집 - 인스턴스를 수정하고 마스터 id로 요약을 반환한다")
    void editInstance() throws Exception {
        connectGoogle();

        editRoutine("instance", "2026-06-20")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recurringEventId").value("r-habit-1"))
                .andExpect(jsonPath("$.data.title").value("운동하기(아침)"));
    }

    @Test
    @DisplayName("scope=following 편집 - 분할 후 새 id 반환 + 체크가 경계로 분리된다")
    void editFollowing() throws Exception {
        connectGoogle();
        seedCheck("r-habit-1", LocalDate.of(2026, 6, 15)); // 분할 전 → 유지
        seedCheck("r-habit-1", LocalDate.of(2026, 6, 25)); // 분할 후 → 이관

        editRoutine("following", "2026-06-20")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recurringEventId").value("r-new-1"));

        assertThat(hasCheck("r-habit-1", LocalDate.of(2026, 6, 15))).isTrue();
        assertThat(hasCheck("r-habit-1", LocalDate.of(2026, 6, 25))).isFalse();
        assertThat(hasCheck("r-new-1", LocalDate.of(2026, 6, 25))).isTrue();
    }

    @Test
    @DisplayName("following 편집인데 instanceDate가 없으면 400")
    void edit_followingNoInstanceDate() throws Exception {
        connectGoogle();

        editRoutine("following", null).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("미연동이면 409(PLN-ERR-003)")
    void edit_notConnected() throws Exception {
        editRoutine("all", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLN-ERR-003"));
    }

    // ---------- DELETE ----------

    @Test
    @DisplayName("scope=all 삭제 - 마스터 삭제 + 시리즈 체크 전부 정리")
    void deleteAll() throws Exception {
        connectGoogle();
        seedCheck("r-habit-1", LocalDate.of(2026, 6, 15));
        seedCheck("r-habit-1", LocalDate.of(2026, 6, 25));

        deleteRoutine("all", null).andExpect(status().isOk());

        assertThat(hasCheck("r-habit-1", LocalDate.of(2026, 6, 15))).isFalse();
        assertThat(hasCheck("r-habit-1", LocalDate.of(2026, 6, 25))).isFalse();
    }

    @Test
    @DisplayName("scope=instance 삭제 - 해당 날짜 체크만 정리")
    void deleteInstance() throws Exception {
        connectGoogle();
        seedCheck("r-habit-1", LocalDate.of(2026, 6, 20));
        seedCheck("r-habit-1", LocalDate.of(2026, 6, 21));

        deleteRoutine("instance", "2026-06-20").andExpect(status().isOk());

        assertThat(hasCheck("r-habit-1", LocalDate.of(2026, 6, 20))).isFalse();
        assertThat(hasCheck("r-habit-1", LocalDate.of(2026, 6, 21))).isTrue();
    }

    @Test
    @DisplayName("scope=following 삭제 - instanceDate 이상 체크 정리(이전은 유지)")
    void deleteFollowing() throws Exception {
        connectGoogle();
        seedCheck("r-habit-1", LocalDate.of(2026, 6, 15));
        seedCheck("r-habit-1", LocalDate.of(2026, 6, 25));

        deleteRoutine("following", "2026-06-20").andExpect(status().isOk());

        assertThat(hasCheck("r-habit-1", LocalDate.of(2026, 6, 15))).isTrue();
        assertThat(hasCheck("r-habit-1", LocalDate.of(2026, 6, 25))).isFalse();
    }

    @Test
    @DisplayName("알 수 없는 scope면 400")
    void delete_unknownScope() throws Exception {
        connectGoogle();

        deleteRoutine("bogus", null).andExpect(status().isBadRequest());
    }

    private static HttpServer createStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/calendar/v3/calendars/primary/events", RoutineSeriesControllerTest::handle);
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String suffix = path.substring(path.indexOf("/events") + "/events".length());

        if (suffix.isEmpty() || suffix.equals("/")) {
            // base: POST=forked 시리즈 생성, GET=마스터 목록(findForkedSeries→없음)
            respond(exchange, 200, "POST".equals(method) ? FORKED : EMPTY_ITEMS);
            return;
        }
        String rest = suffix.startsWith("/") ? suffix.substring(1) : suffix;
        if (rest.endsWith("/instances")) {
            respond(exchange, 200, INSTANCES);
            return;
        }
        // single id
        switch (method) {
            case "DELETE" -> respondNoBody(exchange);
            case "PATCH" -> respond(exchange, 200, EDITED);
            default -> respond(exchange, "r-habit-1".equals(rest) ? 200 : 404,
                    "r-habit-1".equals(rest) ? HABIT_MASTER : "{\"error\":\"notFound\"}");
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

    private static void respondNoBody(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }
}
