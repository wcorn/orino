package ds.project.orino.planner.google.task;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.google.entity.GoogleAccount;
import ds.project.orino.domain.planner.google.repository.GoogleAccountRepository;
import ds.project.orino.planner.google.calendar.dto.PlannerTask;
import ds.project.orino.planner.google.task.dto.TaskCreateRequest;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class GoogleTasksQueryServiceTest {

    private static final HttpServer TASKS_STUB = createStub();
    private static final AtomicInteger LIST_CALL_COUNT = new AtomicInteger();
    private static final String LIST_BODY = """
            {"items":[{"id":"t1","title":"리포트 제출","status":"needsAction","due":"2026-06-12T00:00:00.000Z"}]}""";

    @Autowired
    private GoogleTasksQueryService tasksQueryService;
    @Autowired
    private GoogleTasksCommandService tasksCommandService;
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
    void setUp() {
        LIST_CALL_COUNT.set(0);
        dbCleaner.clean();
        memberId = memberRepository.save(MemberFixture.create()).getId();
    }

    private void connect() {
        accountRepository.save(new GoogleAccount(
                memberId, "refresh", "scope", "me@gmail.com", "primary", "@default"));
        // access token을 미리 캐시해 refresh grant 없이 tasks 호출만 일어나게 한다.
        accessTokenRepository.save(memberId, "test-access", Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("미연동이면 GOOGLE_NOT_CONNECTED를 전파하고 Google을 호출하지 않는다")
    void listTasks_notConnected() {
        assertThatThrownBy(() -> tasksQueryService.listTasks(memberId, false))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GOOGLE_NOT_CONNECTED);

        assertThat(LIST_CALL_COUNT.get()).isZero();
    }

    @Test
    @DisplayName("할 일 목록을 정규화해 반환한다")
    void listTasks_normalizes() {
        connect();

        List<PlannerTask> tasks = tasksQueryService.listTasks(memberId, false);

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).id()).isEqualTo("t1");
        assertThat(tasks.get(0).due()).isEqualTo("2026-06-12");
        assertThat(tasks.get(0).completed()).isFalse();
        assertThat(tasks.get(0).source()).isEqualTo("google");
    }

    @Test
    @DisplayName("재조회는 단기 캐시로 흡수해 Google을 한 번만 호출한다")
    void listTasks_caches() {
        connect();

        List<PlannerTask> first = tasksQueryService.listTasks(memberId, false);
        List<PlannerTask> second = tasksQueryService.listTasks(memberId, false);

        assertThat(LIST_CALL_COUNT.get()).isEqualTo(1);
        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(second.get(0).id()).isEqualTo("t1");
    }

    @Test
    @DisplayName("쓰기(생성) 후에는 캐시가 무효화되어 다음 조회가 Google을 다시 호출한다")
    void write_evictsCache() {
        connect();

        tasksQueryService.listTasks(memberId, false); // 1회 호출 + 캐시 적재
        tasksCommandService.create(memberId, new TaskCreateRequest("새 할일", "2026-06-12", null)); // evict
        tasksQueryService.listTasks(memberId, false); // 캐시 미스 → 재호출

        assertThat(LIST_CALL_COUNT.get()).isEqualTo(2);
    }

    private static HttpServer createStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/tasks/v1/lists/@default/tasks", exchange -> {
                if ("GET".equals(exchange.getRequestMethod())) {
                    LIST_CALL_COUNT.incrementAndGet();
                    respond(exchange, LIST_BODY);
                } else {
                    respond(exchange, """
                            {"id":"t1","title":"새 할일","status":"needsAction","due":"2026-06-12T00:00:00.000Z"}""");
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
