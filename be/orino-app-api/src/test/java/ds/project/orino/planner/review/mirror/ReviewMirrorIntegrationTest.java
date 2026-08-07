package ds.project.orino.planner.review.mirror;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.google.entity.GoogleAccount;
import ds.project.orino.domain.planner.google.repository.GoogleAccountRepository;
import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.ReviewCalendarMirror;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.repository.ReviewCalendarMirrorRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 복습 → 보조 캘린더 미러 RM1 통합 테스트(MockMvc + TestContainers + Google 스텁).
 * 플래시카드 생성/복습 완료 훅 → reconcile(커밋 후)가 보조 캘린더에 묶음 종일 이벤트를 upsert/삭제하는지 검증한다.
 */
class ReviewMirrorIntegrationTest extends ApiTestSupport {

    private static final String CALENDAR_ID = "review-cal";

    private static final HttpServer CALENDAR_STUB = createStub();
    private static final List<CapturedRequest> REQUESTS = new ArrayList<>();
    private static final Set<String> NOT_FOUND_EVENT_IDS = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger EVENT_SEQ = new AtomicInteger();

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private StudyMaterialRepository studyMaterialRepository;
    @Autowired
    private ReviewScheduleRepository reviewScheduleRepository;
    @Autowired
    private ReviewCalendarMirrorRepository mirrorRepository;
    @Autowired
    private GoogleAccountRepository googleAccountRepository;
    @Autowired
    private GoogleAccessTokenRepository accessTokenRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private Clock clock;

    private Long memberId;
    private Long materialId;
    private String authHeader;
    private LocalDate today;

    @DynamicPropertySource
    static void googleProperties(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + CALENDAR_STUB.getAddress().getPort();
        registry.add("planner.google.client-id", () -> "test-client-id");
        registry.add("planner.google.client-secret", () -> "test-client-secret");
        registry.add("planner.google.oauth.calendar-api-base-url", () -> base);
    }

    @AfterAll
    static void stopStub() {
        CALENDAR_STUB.stop(0);
    }

    @BeforeEach
    void setUp() throws Exception {
        REQUESTS.clear();
        NOT_FOUND_EVENT_IDS.clear();
        EVENT_SEQ.set(0);
        dbCleaner.clean();

        Member member = memberRepository.save(MemberFixture.create());
        memberId = member.getId();
        materialId = studyMaterialRepository.save(
                new StudyMaterial(memberId, "수학", MaterialType.BOOK)).getId();

        GoogleAccount account = new GoogleAccount(
                memberId, "refresh", "scope", "me@gmail.com", "primary", "@default");
        account.enableReviewMirror(CALENDAR_ID);
        googleAccountRepository.save(account);
        accessTokenRepository.save(memberId, "test-access", Duration.ofMinutes(30));

        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        today = testToday(clock);
    }

    @Test
    @DisplayName("연동 만료(revoked) 중에는 미러를 건너뛴다 — 매핑·Google 호출 없음")
    void revoked_account_skips_mirror() throws Exception {
        GoogleAccount account = googleAccountRepository.findByMemberId(memberId).orElseThrow();
        account.markRevoked(); // 미러는 켜져 있으나 토큰이 무효화된 상태
        googleAccountRepository.save(account);

        createCard("Q1");

        assertThat(mirrorRepository.findAllByMemberId(memberId)).isEmpty();
        assertThat(REQUESTS).isEmpty();
    }

    @Test
    @DisplayName("플래시카드 생성 → 첫 복습 dueDate에 '복습 1개' 종일 이벤트를 insert하고 매핑을 저장한다")
    void create_inserts_bundle() throws Exception {
        createCard("Q1");

        LocalDate dueDate = today.plusDays(1);
        ReviewCalendarMirror mirror =
                mirrorRepository.findByMemberIdAndDueDate(memberId, dueDate).orElseThrow();
        assertThat(mirror.getPendingCount()).isEqualTo(1);
        assertThat(mirror.getGoogleEventId()).isEqualTo("evt-1");

        CapturedRequest insert = last();
        assertThat(insert.method).isEqualTo("POST");
        assertThat(insert.path).isEqualTo("/calendar/v3/calendars/" + CALENDAR_ID + "/events");
        assertThat(insert.body).contains("\"summary\":\"복습 1개\"");
        assertThat(insert.body).contains("\"수학: 1개\"");
        assertThat(insert.body).contains("\"start\":{\"date\":\"" + dueDate + "\"}");
    }

    @Test
    @DisplayName("같은 날 두 번째 카드 → 기존 이벤트를 '복습 2개'로 patch하고 count를 갱신한다")
    void second_card_patches_bundle() throws Exception {
        createCard("Q1");
        createCard("Q2");

        LocalDate dueDate = today.plusDays(1);
        ReviewCalendarMirror mirror =
                mirrorRepository.findByMemberIdAndDueDate(memberId, dueDate).orElseThrow();
        assertThat(mirror.getPendingCount()).isEqualTo(2);
        assertThat(mirror.getGoogleEventId()).isEqualTo("evt-1");

        CapturedRequest patch = last();
        assertThat(patch.method).isEqualTo("PATCH");
        assertThat(patch.path).isEqualTo("/calendar/v3/calendars/" + CALENDAR_ID + "/events/evt-1");
        assertThat(patch.body).contains("\"summary\":\"복습 2개\"");
        assertThat(patch.body).contains("\"수학: 2개\"");
    }

    @Test
    @DisplayName("GOOD 완료 → 완료 dueDate 묶음을 삭제하고 다음 dueDate에 새 묶음을 insert한다")
    void good_completion_deletes_old_and_inserts_new() throws Exception {
        createCard("Q1");
        Long reviewId = onlyReviewId();

        complete(reviewId, "GOOD");

        // 완료된 dueDate(today+1) 묶음은 N=0 → 삭제
        assertThat(mirrorRepository.findByMemberIdAndDueDate(memberId, today.plusDays(1))).isEmpty();
        // 다음 복습(GOOD: 직전 1일 × ease 2.50 → interval 3) dueDate(today+3) 묶음은 새로 insert
        ReviewCalendarMirror next =
                mirrorRepository.findByMemberIdAndDueDate(memberId, today.plusDays(3)).orElseThrow();
        assertThat(next.getPendingCount()).isEqualTo(1);
        assertThat(next.getGoogleEventId()).isEqualTo("evt-2");

        assertThat(REQUESTS).anyMatch(r -> r.method.equals("DELETE")
                && r.path.endsWith("/events/evt-1"));
    }

    @Test
    @DisplayName("AGAIN 완료 → 당일 +10분 재복습은 04:00 묶음에서 제외되어 미러되지 않는다")
    void again_completion_excludes_relearn() throws Exception {
        createCard("Q1");
        Long reviewId = onlyReviewId();

        complete(reviewId, "AGAIN");

        // 완료된 today+1 묶음은 삭제, AGAIN(today, +10분)은 미러 없음 → 멤버 미러 0건
        assertThat(mirrorRepository.findAllByMemberId(memberId)).isEmpty();
        assertThat(mirrorRepository.findByMemberIdAndDueDate(memberId, today)).isEmpty();
        // 다음 복습은 PENDING으로 존재하지만(2건), 04:00 정각이 아니라 묶음에 안 잡힌다
        assertThat(reviewScheduleRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("self-heal: Google에서 이벤트가 사라지면(404) patch 실패 후 재생성하고 새 eventId로 매핑을 갱신한다")
    void self_heal_recreates_on_404() throws Exception {
        createCard("Q1"); // evt-1 매핑 생성
        NOT_FOUND_EVENT_IDS.add("evt-1"); // 사용자가 Google에서 삭제했다고 가정

        createCard("Q2"); // reconcile → patch evt-1(404) → 재생성 evt-2

        ReviewCalendarMirror mirror =
                mirrorRepository.findByMemberIdAndDueDate(memberId, today.plusDays(1)).orElseThrow();
        assertThat(mirror.getPendingCount()).isEqualTo(2);
        assertThat(mirror.getGoogleEventId()).isEqualTo("evt-2");

        assertThat(REQUESTS).anyMatch(r -> r.method.equals("PATCH") && r.path.endsWith("/events/evt-1"));
        CapturedRequest recreate = last();
        assertThat(recreate.method).isEqualTo("POST");
        assertThat(recreate.path).isEqualTo("/calendar/v3/calendars/" + CALENDAR_ID + "/events");
        assertThat(recreate.body).contains("\"summary\":\"복습 2개\"");
    }

    private void createCard(String front) throws Exception {
        mockMvc.perform(post("/api/planner/materials/{id}/flashcards", materialId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"front\":\"" + front + "\",\"back\":\"A\"}"))
                .andExpect(status().isCreated());
    }

    private void complete(Long reviewId, String rating) throws Exception {
        mockMvc.perform(post("/api/planner/reviews/{id}/complete", reviewId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"" + rating + "\"}"))
                .andExpect(status().isOk());
    }

    private Long onlyReviewId() {
        List<ReviewSchedule> all = reviewScheduleRepository.findAll();
        assertThat(all).hasSize(1);
        return all.get(0).getId();
    }

    private CapturedRequest last() {
        return REQUESTS.get(REQUESTS.size() - 1);
    }

    private static HttpServer createStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/calendar/v3/calendars", ReviewMirrorIntegrationTest::handle);
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        REQUESTS.add(new CapturedRequest(method, path, body));

        String eventId = path.substring(path.lastIndexOf('/') + 1);
        boolean isCollection = path.endsWith("/events");
        if (!isCollection && NOT_FOUND_EVENT_IDS.contains(eventId)) {
            respond(exchange, 404, "{\"error\":\"notFound\"}");
            return;
        }
        if ("DELETE".equals(method)) {
            respond(exchange, 204, null);
            return;
        }
        if (isCollection && "POST".equals(method)) {
            respond(exchange, 200, "{\"id\":\"evt-" + EVENT_SEQ.incrementAndGet() + "\"}");
            return;
        }
        respond(exchange, 200, "{\"id\":\"" + eventId + "\"}"); // PATCH
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private record CapturedRequest(String method, String path, String body) {
    }
}
