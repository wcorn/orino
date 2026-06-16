package ds.project.orino.planner.google.task;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoogleTaskControllerTest extends ApiTestSupport {

    private static final HttpServer TASKS_STUB = createStub();
    private static final String NEEDS_ACTION = """
            {"id":"t1","title":"리포트 제출","status":"needsAction","due":"2026-06-12T00:00:00.000Z"}""";
    private static final String COMPLETED = """
            {"id":"t1","title":"리포트 제출","status":"completed","due":"2026-06-12T00:00:00.000Z"}""";
    private static final String LIST_BODY = "{\"items\":[" + NEEDS_ACTION + "]}";

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
        String base = "http://127.0.0.1:" + TASKS_STUB.getAddress().getPort();
        registry.add("planner.google.client-id", () -> "test-client-id");
        registry.add("planner.google.client-secret", () -> "test-client-secret");
        registry.add("planner.google.oauth.tasks-api-base-url", () -> base);
    }

    @AfterAll
    static void stopStub() {
        TASKS_STUB.stop(0);
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
    @DisplayName("GET - 할 일 목록을 정규화해 반환한다")
    void list() throws Exception {
        connectGoogle();

        mockMvc.perform(get("/api/planner/tasks").header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks.length()").value(1))
                .andExpect(jsonPath("$.data.tasks[0].id").value("t1"))
                .andExpect(jsonPath("$.data.tasks[0].due").value("2026-06-12"))
                .andExpect(jsonPath("$.data.tasks[0].completed").value(false));
    }

    @Test
    @DisplayName("POST - 할 일을 생성하고 201로 반환한다")
    void create() throws Exception {
        connectGoogle();

        mockMvc.perform(post("/api/planner/tasks")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"리포트 제출","due":"2026-06-12"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("t1"))
                .andExpect(jsonPath("$.data.title").value("리포트 제출"))
                .andExpect(jsonPath("$.data.completed").value(false));
    }

    @Test
    @DisplayName("PATCH - completed=true로 완료 토글한다")
    void completeToggle() throws Exception {
        connectGoogle();

        mockMvc.perform(patch("/api/planner/tasks/t1")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"completed":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));
    }

    @Test
    @DisplayName("DELETE - 할 일을 삭제하고 200 success를 반환한다")
    void deleteTask() throws Exception {
        connectGoogle();

        mockMvc.perform(delete("/api/planner/tasks/t1").header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("요청에 성공하였습니다."));
    }

    @Test
    @DisplayName("미연동 상태에서 목록 조회하면 409(PLN-ERR-003)")
    void list_notConnected() throws Exception {
        mockMvc.perform(get("/api/planner/tasks").header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLN-ERR-003"));
    }

    private static HttpServer createStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/tasks/v1/lists/@default/tasks", exchange -> {
                String method = exchange.getRequestMethod();
                switch (method) {
                    case "DELETE" -> {
                        exchange.sendResponseHeaders(204, -1);
                        exchange.close();
                    }
                    case "GET" -> respond(exchange, LIST_BODY);
                    case "PATCH" -> respond(exchange, COMPLETED);
                    default -> respond(exchange, NEEDS_ACTION);
                }
            });
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
