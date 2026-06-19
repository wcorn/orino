package ds.project.orino.planner.google.routine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.google.entity.GoogleAccount;
import ds.project.orino.domain.planner.google.repository.GoogleAccountRepository;
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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoutineQueryControllerTest extends ApiTestSupport {

    /** singleEvents=false 마스터 목록: habit(종일), schedule(시간), recurrence 없는 비정상 항목(필터 대상). */
    private static final String MASTERS_JSON = """
            {"items":[
              {"id":"r-habit-1","summary":"운동하기",
               "start":{"date":"2026-06-20"},"end":{"date":"2026-06-21"},
               "recurrence":["RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR"],
               "extendedProperties":{"private":{"orinoRoutine":"1","orinoRoutineType":"habit"}}},
              {"id":"r-sched-1","summary":"스탠드업",
               "start":{"dateTime":"2026-06-20T00:00:00Z"},"end":{"dateTime":"2026-06-20T00:15:00Z"},
               "recurrence":["RRULE:FREQ=DAILY"],
               "extendedProperties":{"private":{"orinoRoutine":"1","orinoRoutineType":"schedule"}}},
              {"id":"r-broken","summary":"규칙 없음",
               "start":{"date":"2026-06-20"},"end":{"date":"2026-06-21"},
               "extendedProperties":{"private":{"orinoRoutine":"1","orinoRoutineType":"habit"}}}
            ]}""";

    private static final HttpServer EVENTS_STUB = createStub();

    @Autowired
    private MemberRepository memberRepository;
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
    @DisplayName("GET - 마스터 시리즈를 종류·반복 요약과 함께 반환하고 규칙 없는 항목은 제외한다")
    void list() throws Exception {
        connectGoogle();

        mockMvc.perform(get("/api/planner/routines")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routines.length()").value(2))
                // habit(종일)
                .andExpect(jsonPath("$.data.routines[0].recurringEventId").value("r-habit-1"))
                .andExpect(jsonPath("$.data.routines[0].type").value("habit"))
                .andExpect(jsonPath("$.data.routines[0].allDay").value(true))
                .andExpect(jsonPath("$.data.routines[0].start").value("2026-06-20"))
                .andExpect(jsonPath("$.data.routines[0].recurrence.freq").value("WEEKLY"))
                .andExpect(jsonPath("$.data.routines[0].recurrence.byDay[2]").value("FR"))
                .andExpect(jsonPath("$.data.routines[0].recurrenceText").value("매주 월·수·금"))
                // schedule(시간) - 사용자 TZ(Asia/Seoul, +9)로 정규화
                .andExpect(jsonPath("$.data.routines[1].type").value("schedule"))
                .andExpect(jsonPath("$.data.routines[1].allDay").value(false))
                .andExpect(jsonPath("$.data.routines[1].start").value("2026-06-20T09:00:00"))
                .andExpect(jsonPath("$.data.routines[1].end").value("2026-06-20T09:15:00"))
                .andExpect(jsonPath("$.data.routines[1].recurrenceText").value("매일"));
    }

    @Test
    @DisplayName("미연동 상태에서 조회하면 409(PLN-ERR-003)")
    void list_notConnected() throws Exception {
        mockMvc.perform(get("/api/planner/routines")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLN-ERR-003"));
    }

    private static HttpServer createStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/calendar/v3/calendars/primary/events", exchange ->
                    respond(exchange, 200, MASTERS_JSON));
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
