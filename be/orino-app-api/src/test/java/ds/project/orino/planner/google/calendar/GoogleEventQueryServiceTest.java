package ds.project.orino.planner.google.calendar;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.google.entity.GoogleAccount;
import ds.project.orino.domain.planner.google.repository.GoogleAccountRepository;
import ds.project.orino.planner.google.calendar.dto.GoogleEventsView;
import ds.project.orino.planner.google.calendar.dto.PlannerEvent;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class GoogleEventQueryServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 30);

    private static final HttpServer EVENTS_STUB = createStub();
    private static final AtomicInteger callCount = new AtomicInteger();
    private static volatile String responseBody = "{\"items\":[]}";

    @Autowired
    private GoogleEventQueryService eventQueryService;
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
    void setUp() {
        callCount.set(0);
        responseBody = "{\"items\":[]}";
        dbCleaner.clean();
        memberId = memberRepository.save(MemberFixture.create()).getId();
    }

    private void connect() {
        accountRepository.save(new GoogleAccount(
                memberId, "refresh", "scope", "me@gmail.com", "primary", "@default"));
        // access token을 미리 캐시해 refresh grant 없이 events 호출만 일어나게 한다.
        accessTokenRepository.save(memberId, "test-access", Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("미연동이면 connected=false와 빈 목록을 반환하고 Google을 호출하지 않는다")
    void getEvents_notConnected() {
        GoogleEventsView view = eventQueryService.getEvents(memberId, FROM, TO, SEOUL);

        assertThat(view.connected()).isFalse();
        assertThat(view.events()).isEmpty();
        assertThat(callCount.get()).isZero();
    }

    @Test
    @DisplayName("revoked 연동이면 connected=false")
    void getEvents_revoked() {
        GoogleAccount account = new GoogleAccount(memberId, "r", "s", "me@gmail.com", "primary", "@default");
        account.markRevoked();
        accountRepository.save(account);

        GoogleEventsView view = eventQueryService.getEvents(memberId, FROM, TO, SEOUL);

        assertThat(view.connected()).isFalse();
        assertThat(view.events()).isEmpty();
    }

    @Test
    @DisplayName("시간/종일/반복 일정을 사용자 시간대로 정규화한다")
    void getEvents_normalizes() {
        connect();
        responseBody = """
                {"items":[
                  {"id":"e1","summary":"회의","location":"3층",
                   "start":{"dateTime":"2026-06-10T05:00:00Z"},"end":{"dateTime":"2026-06-10T06:00:00Z"}},
                  {"id":"e2","summary":"여행",
                   "start":{"date":"2026-06-12"},"end":{"date":"2026-06-14"}},
                  {"id":"e3","summary":"스탠드업","recurringEventId":"r1",
                   "start":{"dateTime":"2026-06-11T00:00:00Z"},"end":{"dateTime":"2026-06-11T00:30:00Z"}}
                ]}""";

        GoogleEventsView view = eventQueryService.getEvents(memberId, FROM, TO, SEOUL);

        assertThat(view.connected()).isTrue();
        assertThat(view.events()).hasSize(3);

        PlannerEvent timed = find(view.events(), "e1");
        assertThat(timed.allDay()).isFalse();
        assertThat(timed.title()).isEqualTo("회의");
        assertThat(timed.start()).isEqualTo("2026-06-10T14:00:00");
        assertThat(timed.end()).isEqualTo("2026-06-10T15:00:00");
        assertThat(timed.location()).isEqualTo("3층");
        assertThat(timed.recurring()).isFalse();
        assertThat(timed.source()).isEqualTo("google");

        PlannerEvent allDay = find(view.events(), "e2");
        assertThat(allDay.allDay()).isTrue();
        assertThat(allDay.start()).isEqualTo("2026-06-12");
        assertThat(allDay.end()).isEqualTo("2026-06-13"); // 배타적 종료(06-14) → 포함 마지막날

        PlannerEvent recurring = find(view.events(), "e3");
        assertThat(recurring.recurring()).isTrue();
        assertThat(recurring.start()).isEqualTo("2026-06-11T09:00:00");
    }

    @Test
    @DisplayName("같은 구간 재조회는 단기 캐시로 흡수해 Google을 한 번만 호출한다")
    void getEvents_caches() {
        connect();
        responseBody = """
                {"items":[{"id":"e1","summary":"회의",
                 "start":{"dateTime":"2026-06-10T05:00:00Z"},"end":{"dateTime":"2026-06-10T06:00:00Z"}}]}""";

        GoogleEventsView first = eventQueryService.getEvents(memberId, FROM, TO, SEOUL);
        GoogleEventsView second = eventQueryService.getEvents(memberId, FROM, TO, SEOUL);

        assertThat(callCount.get()).isEqualTo(1);
        assertThat(first.events()).hasSize(1);
        assertThat(second.events()).hasSize(1);
        assertThat(second.events().get(0).start()).isEqualTo("2026-06-10T14:00:00");
    }

    private static PlannerEvent find(List<PlannerEvent> events, String id) {
        return events.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    private static HttpServer createStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/calendar/v3/calendars/primary/events", exchange -> {
                callCount.incrementAndGet();
                respond(exchange, 200, responseBody);
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
