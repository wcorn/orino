package ds.project.orino.planner.holiday;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.holiday.repository.HolidayRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HolidayControllerTest extends ApiTestSupport {

    private static final String ARRAY_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
             "body":{"items":{"item":[
               {"dateKind":"01","dateName":"지방선거일","isHoliday":"Y","locdate":20260603},
               {"dateKind":"01","dateName":"현충일","isHoliday":"Y","locdate":20260606}
             ]},"numOfRows":100,"pageNo":1,"totalCount":2}}}""";
    private static final String SINGLE_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
             "body":{"items":{"item":
               {"dateKind":"01","dateName":"신정","isHoliday":"Y","locdate":20260101}
             },"numOfRows":100,"pageNo":1,"totalCount":1}}}""";
    private static final String EMPTY_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
             "body":{"items":"","numOfRows":100,"pageNo":1,"totalCount":0}}}""";

    private static volatile String responseBody = ARRAY_BODY;
    private static final HttpServer STUB = createStub();

    @Autowired
    private HolidaySyncService holidaySyncService;
    @Autowired
    private HolidayRepository holidayRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;

    @DynamicPropertySource
    static void holidayProperties(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + STUB.getAddress().getPort();
        registry.add("holiday.base-url", () -> base);
        registry.add("holiday.service-key", () -> "test-key");
        registry.add("holiday.sync-years", () -> "1");
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
    }

    @BeforeEach
    void setUp() throws Exception {
        responseBody = ARRAY_BODY;
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    @Test
    @DisplayName("동기화 후 구간 조회 시 공휴일을 날짜·이름으로 반환한다")
    void syncThenList() throws Exception {
        holidaySyncService.sync(2026);

        mockMvc.perform(get("/api/planner/holidays")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].date").value("2026-06-03"))
                .andExpect(jsonPath("$.data[0].name").value("지방선거일"))
                .andExpect(jsonPath("$.data[1].date").value("2026-06-06"))
                .andExpect(jsonPath("$.data[1].name").value("현충일"));
    }

    @Test
    @DisplayName("items가 단일 객체로 와도 파싱한다")
    void singleObjectItem() {
        responseBody = SINGLE_BODY;

        int count = holidaySyncService.sync(2026);

        assertThat(count).isEqualTo(1);
        assertThat(holidayRepository.findByDate(java.time.LocalDate.of(2026, 1, 1)))
                .isPresent();
    }

    @Test
    @DisplayName("공휴일이 0건(빈 문자열)이어도 오류 없이 처리한다")
    void emptyItems() {
        responseBody = EMPTY_BODY;

        int count = holidaySyncService.sync(2026);

        assertThat(count).isZero();
    }

    @Test
    @DisplayName("같은 날짜 재동기화는 멱등(중복 생성 없음)")
    void idempotentSync() {
        holidaySyncService.sync(2026);
        holidaySyncService.sync(2026);

        assertThat(holidayRepository.findAll()).hasSize(2);
    }

    private static HttpServer createStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/getRestDeInfo", exchange -> respond(exchange, responseBody));
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
