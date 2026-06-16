package ds.project.orino.planner.google.calendar;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.repository.FlashcardRepository;
import ds.project.orino.domain.planner.google.entity.GoogleAccount;
import ds.project.orino.domain.planner.google.repository.GoogleAccountRepository;
import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlannerCalendarControllerTest extends ApiTestSupport {

    private static final String EMPTY_ITEMS = "{\"items\":[]}";

    private static final HttpServer EVENTS_STUB = createStub();
    private static volatile int responseStatus = 200;
    private static volatile String responseBody = EMPTY_ITEMS;
    private static volatile String tasksBody = EMPTY_ITEMS;

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private StudyMaterialRepository studyMaterialRepository;
    @Autowired
    private FlashcardRepository flashcardRepository;
    @Autowired
    private ReviewScheduleRepository reviewScheduleRepository;
    @Autowired
    private GoogleAccountRepository googleAccountRepository;
    @Autowired
    private GoogleAccessTokenRepository accessTokenRepository;
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
        responseStatus = 200;
        responseBody = """
                {"items":[{"id":"e1","summary":"회의",
                 "start":{"dateTime":"2026-06-10T05:00:00Z"},"end":{"dateTime":"2026-06-10T06:00:00Z"}}]}""";
        tasksBody = EMPTY_ITEMS;
        dbCleaner.clean();
        Member member = memberRepository.save(MemberFixture.create());
        memberId = member.getId();
        StudyMaterial material = studyMaterialRepository.save(
                new StudyMaterial(memberId, "이펙티브 자바", MaterialType.BOOK));
        Flashcard card = flashcardRepository.save(new Flashcard(memberId, material.getId(), "Q", "A"));
        reviewScheduleRepository.save(new ReviewSchedule(
                memberId, card.getId(), 1, atTestZone(LocalDate.of(2026, 6, 11).atTime(4, 0)),
                6, new BigDecimal("2.50")));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    private void connectGoogle() {
        googleAccountRepository.save(new GoogleAccount(
                memberId, "refresh", "scope", "me@gmail.com", "primary", "@default"));
        accessTokenRepository.save(memberId, "test-access", Duration.ofMinutes(30));
    }

    private org.springframework.test.web.servlet.ResultActions requestFeed(String from, String to) throws Exception {
        return mockMvc.perform(get("/api/planner/calendar")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .param("from", from)
                .param("to", to));
    }

    @Test
    @DisplayName("연동 시 일정과 복습을 한 응답으로 병합한다")
    void mergesEventsAndReviews() throws Exception {
        connectGoogle();

        requestFeed("2026-06-01", "2026-06-30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.googleConnected").value(true))
                .andExpect(jsonPath("$.data.partial").value(false))
                .andExpect(jsonPath("$.data.events.length()").value(1))
                .andExpect(jsonPath("$.data.events[0].title").value("회의"))
                .andExpect(jsonPath("$.data.reviews.length()").value(1))
                .andExpect(jsonPath("$.data.reviews[0].readOnly").value(true))
                .andExpect(jsonPath("$.data.reviews[0].source").value("review"))
                .andExpect(jsonPath("$.data.reviews[0].materialTitle").value("이펙티브 자바"))
                .andExpect(jsonPath("$.data.tasks.length()").value(0));
    }

    @Test
    @DisplayName("Google 일정이 실패해도 200으로 복습을 반환하고 partial+errors로 표기한다")
    void partialWhenGoogleFails() throws Exception {
        connectGoogle();
        responseStatus = 500;
        responseBody = "{\"error\":\"backendError\"}";

        requestFeed("2026-06-01", "2026-06-30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.partial").value(true))
                .andExpect(jsonPath("$.data.errors.length()").value(1))
                .andExpect(jsonPath("$.data.errors[0].source").value("google-events"))
                .andExpect(jsonPath("$.data.events.length()").value(0))
                .andExpect(jsonPath("$.data.reviews.length()").value(1));
    }

    @Test
    @DisplayName("미연동이면 googleConnected=false, events 빈 배열, 복습은 정상")
    void notConnected() throws Exception {
        requestFeed("2026-06-01", "2026-06-30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.googleConnected").value(false))
                .andExpect(jsonPath("$.data.partial").value(false))
                .andExpect(jsonPath("$.data.events.length()").value(0))
                .andExpect(jsonPath("$.data.reviews.length()").value(1));
    }

    @Test
    @DisplayName("to가 from보다 빠르면 400")
    void invalidRange() throws Exception {
        requestFeed("2026-06-30", "2026-06-01")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("연동 시 due가 기간 내인 할 일만 피드 tasks에 합류한다(정규화)")
    void mergesTasks() throws Exception {
        connectGoogle();
        tasksBody = """
                {"items":[
                  {"id":"t1","title":"리포트 제출","status":"needsAction","due":"2026-06-12T00:00:00.000Z"},
                  {"id":"t2","title":"기간 밖","status":"needsAction","due":"2026-07-15T00:00:00.000Z"},
                  {"id":"t3","title":"마감 없음","status":"needsAction"}
                ]}""";

        requestFeed("2026-06-01", "2026-06-30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.partial").value(false))
                .andExpect(jsonPath("$.data.tasks.length()").value(1))
                .andExpect(jsonPath("$.data.tasks[0].id").value("t1"))
                .andExpect(jsonPath("$.data.tasks[0].due").value("2026-06-12"))
                .andExpect(jsonPath("$.data.tasks[0].completed").value(false))
                .andExpect(jsonPath("$.data.tasks[0].source").value("google"));
    }

    private static HttpServer createStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/calendar/v3/calendars/primary/events", exchange ->
                    respond(exchange, responseStatus, responseBody));
            server.createContext("/tasks/v1/lists/@default/tasks", exchange ->
                    respond(exchange, 200, tasksBody));
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
