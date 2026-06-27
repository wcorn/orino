package ds.project.orino.planner.google.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ds.project.orino.planner.google.config.GoogleApiConfig;
import ds.project.orino.planner.google.config.GoogleApiProperties;
import ds.project.orino.planner.google.config.GoogleOAuthProperties;
import ds.project.orino.planner.google.token.GoogleTokenProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GoogleCalendarClient — 보조 캘린더 종일 미러")
class GoogleCalendarClientTest {

    private static final Long MEMBER_ID = 7L;
    private static final String CALENDAR_ID = "orino-review-cal";

    private HttpServer server;
    private GoogleCalendarClient client;
    private final List<CapturedRequest> requests = new ArrayList<>();
    private volatile String nextResponse = "{}";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws IOException {
        requests.clear();
        nextResponse = "{}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/calendar/v3/calendars", this::handle);
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        GoogleApiProperties apiProps = new GoogleApiProperties(
                "cid", "secret", "http://localhost/cb",
                List.of("scope"), Duration.ofSeconds(2), Duration.ofSeconds(5),
                2, Duration.ofMillis(1));
        RestClient restClient = new GoogleApiConfig().googleRestClient(apiProps, new SimpleMeterRegistry());
        GoogleOAuthProperties oauthProps = new GoogleOAuthProperties(
                null, null, null, baseUrl, null, null);

        // 토큰 공급은 검증 대상이 아니므로, 콜백을 더미 토큰으로 그대로 실행한다.
        GoogleTokenProvider tokenProvider = mock(GoogleTokenProvider.class);
        when(tokenProvider.executeWithRetry(any(), any())).thenAnswer(inv -> {
            Function<String, Object> call = inv.getArgument(1);
            return call.apply("test-token");
        });

        client = new GoogleCalendarClient(tokenProvider, restClient, oauthProps);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("createSecondaryCalendar는 calendars.insert로 보조 캘린더를 만들고 id를 반환한다")
    void createSecondaryCalendar() {
        nextResponse = "{\"id\":\"cal-new@group.calendar.google.com\",\"summary\":\"orino 복습\"}";

        String calendarId = client.createSecondaryCalendar(MEMBER_ID, "orino 복습");

        assertThat(calendarId).isEqualTo("cal-new@group.calendar.google.com");
        CapturedRequest req = last();
        assertThat(req.method).isEqualTo("POST");
        assertThat(req.path).isEqualTo("/calendar/v3/calendars");
        assertThat(req.body).contains("\"summary\":\"orino 복습\"");
    }

    @Test
    @DisplayName("insertAllDayEvent는 종일(date) 이벤트 + 설명 + useDefault 알림으로 생성하고 eventId를 반환한다")
    void insertAllDayEvent() {
        nextResponse = "{\"id\":\"evt-1\"}";

        String eventId = client.insertAllDayEvent(
                MEMBER_ID, CALENDAR_ID, "복습 2개", "수학: 2개", LocalDate.of(2026, 6, 20));

        assertThat(eventId).isEqualTo("evt-1");
        CapturedRequest req = last();
        assertThat(req.method).isEqualTo("POST");
        assertThat(req.path).isEqualTo("/calendar/v3/calendars/" + CALENDAR_ID + "/events");
        assertThat(req.body).contains("\"summary\":\"복습 2개\"");
        assertThat(req.body).contains("\"description\":\"수학: 2개\"");
        // 종일은 date(시간 없음), Google 종료는 배타적 → end=date+1일
        assertThat(req.body).contains("\"start\":{\"date\":\"2026-06-20\"}");
        assertThat(req.body).contains("\"end\":{\"date\":\"2026-06-21\"}");
        assertThat(req.body).contains("\"reminders\":{\"useDefault\":true}");
        // 시간 일정 필드는 직렬화에서 제외
        assertThat(req.body).doesNotContain("dateTime");
        assertThat(req.body).doesNotContain("timeZone");
    }

    @Test
    @DisplayName("patchAllDayEvent는 제목·설명만 patch한다(start/end 미포함)")
    void patchAllDayEvent() {
        nextResponse = "{\"id\":\"evt-1\",\"summary\":\"복습 3개\"}";

        client.patchAllDayEvent(MEMBER_ID, CALENDAR_ID, "evt-1", "복습 3개", "수학: 3개");

        CapturedRequest req = last();
        assertThat(req.method).isEqualTo("PATCH");
        assertThat(req.path).isEqualTo("/calendar/v3/calendars/" + CALENDAR_ID + "/events/evt-1");
        assertThat(req.body).contains("\"summary\":\"복습 3개\"");
        assertThat(req.body).contains("\"description\":\"수학: 3개\"");
        assertThat(req.body).doesNotContain("start");
        assertThat(req.body).doesNotContain("end");
    }

    @Test
    @DisplayName("deleteEvent(calendarId,eventId)는 보조 캘린더의 이벤트를 DELETE한다")
    void deleteEventOnCalendar() {
        client.deleteEvent(MEMBER_ID, CALENDAR_ID, "evt-1");

        CapturedRequest req = last();
        assertThat(req.method).isEqualTo("DELETE");
        assertThat(req.path).isEqualTo("/calendar/v3/calendars/" + CALENDAR_ID + "/events/evt-1");
    }

    private CapturedRequest last() {
        return requests.get(requests.size() - 1);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new CapturedRequest(
                exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body));

        if ("DELETE".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        byte[] bytes = nextResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private record CapturedRequest(String method, String path, String body) {
    }
}
