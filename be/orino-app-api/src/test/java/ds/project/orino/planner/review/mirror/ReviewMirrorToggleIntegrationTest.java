package ds.project.orino.planner.review.mirror;

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
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 복습 미러 토글 API(RM2) 통합 테스트(MockMvc + TestContainers + Google 스텁).
 * PUT /api/planner/reviews/mirror 의 ON(보조 캘린더 생성 + 백필) / OFF(정리) / 미연동(409)을 검증한다.
 */
class ReviewMirrorToggleIntegrationTest extends ApiTestSupport {

    private static final BigDecimal EF = new BigDecimal("2.50");

    private static final HttpServer CALENDAR_STUB = createStub();
    private static final List<CapturedRequest> REQUESTS = new ArrayList<>();
    private static final AtomicInteger EVENT_SEQ = new AtomicInteger();

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private StudyMaterialRepository studyMaterialRepository;
    @Autowired
    private FlashcardRepository flashcardRepository;
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
        EVENT_SEQ.set(0);
        dbCleaner.clean();
        Member member = memberRepository.save(MemberFixture.create());
        memberId = member.getId();
        materialId = studyMaterialRepository.save(
                new StudyMaterial(memberId, "수학", MaterialType.BOOK)).getId();
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        today = clock.instant().atZone(TEST_ZONE).toLocalDate();
    }

    @Test
    @DisplayName("ON - 미연동이면 409 PLN-ERR-003")
    void enable_notConnected_returns_409() throws Exception {
        toggle(true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLN-ERR-003"));
    }

    @Test
    @DisplayName("ON - 보조 캘린더를 생성하고 PENDING 전 날짜를 백필해 묶음을 만든다")
    void enable_creates_calendar_and_backfills() throws Exception {
        connectGoogle(null, false); // 보조 캘린더 없음, 미러 OFF
        accessTokenRepository.save(memberId, "test-access", Duration.ofMinutes(30));
        seedPendingReview(today.plusDays(1)); // 카드1
        seedPendingReview(today.plusDays(3)); // 카드2

        toggle(true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.reviewCalendarId").value("review-cal-new"));

        // 보조 캘린더 생성(calendars.insert) 1회
        assertThat(REQUESTS).anyMatch(r -> r.method.equals("POST") && r.path.equals("/calendar/v3/calendars"));
        // 두 날짜 백필 → 묶음 행 2개
        assertThat(mirrorRepository.findAllByMemberId(memberId)).hasSize(2);
        assertThat(mirrorRepository.findByMemberIdAndDueDate(memberId, today.plusDays(1))).isPresent();
        assertThat(mirrorRepository.findByMemberIdAndDueDate(memberId, today.plusDays(3))).isPresent();
        // 계정 enabled + calendarId 저장
        GoogleAccount account = googleAccountRepository.findByMemberId(memberId).orElseThrow();
        assertThat(account.isReviewMirrorEnabled()).isTrue();
        assertThat(account.getReviewCalendarId()).isEqualTo("review-cal-new");
    }

    @Test
    @DisplayName("OFF - mirror 이벤트·행을 정리하고 enabled=0, 보조 캘린더 ID는 보존한다")
    void disable_cleans_up_keeps_calendar() throws Exception {
        connectGoogle("review-cal", true); // 미러 ON, 보조 캘린더 보유
        accessTokenRepository.save(memberId, "test-access", Duration.ofMinutes(30));
        mirrorRepository.save(new ReviewCalendarMirror(
                memberId, today.plusDays(1), "evt-1", 1, clock.instant()));
        mirrorRepository.save(new ReviewCalendarMirror(
                memberId, today.plusDays(2), "evt-2", 1, clock.instant()));

        toggle(false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.reviewCalendarId").value("review-cal"));

        // Google 이벤트 2건 삭제
        assertThat(REQUESTS).filteredOn(r -> r.method.equals("DELETE")).hasSize(2);
        // 매핑 전부 정리
        assertThat(mirrorRepository.findAllByMemberId(memberId)).isEmpty();
        // 계정 비활성 + calendarId 보존
        GoogleAccount account = googleAccountRepository.findByMemberId(memberId).orElseThrow();
        assertThat(account.isReviewMirrorEnabled()).isFalse();
        assertThat(account.getReviewCalendarId()).isEqualTo("review-cal");
    }

    @Test
    @DisplayName("ON - 오버듀(지난 날) PENDING도 백필해 묶음을 유지한다")
    void enable_backfills_overdue() throws Exception {
        connectGoogle(null, false);
        accessTokenRepository.save(memberId, "test-access", Duration.ofMinutes(30));
        seedPendingReview(today.minusDays(2)); // 지난 날인데 아직 PENDING(오버듀)

        toggle(true).andExpect(status().isOk());

        assertThat(mirrorRepository.findByMemberIdAndDueDate(memberId, today.minusDays(2)))
                .isPresent();
    }

    @Test
    @DisplayName("ON - 같은 날 여러 자료는 설명을 자료 제목별로 묶는다")
    void enable_groups_description_by_material() throws Exception {
        connectGoogle(null, false);
        accessTokenRepository.save(memberId, "test-access", Duration.ofMinutes(30));
        Long english = studyMaterialRepository.save(
                new StudyMaterial(memberId, "영어", MaterialType.BOOK)).getId();
        seedPendingReviewIn(materialId, today.plusDays(1)); // 수학 1개
        seedPendingReviewIn(english, today.plusDays(1));    // 영어 1개

        toggle(true).andExpect(status().isOk());

        CapturedRequest insert = REQUESTS.stream()
                .filter(r -> r.method.equals("POST") && r.path.endsWith("/events"))
                .reduce((first, second) -> second).orElseThrow();
        assertThat(insert.body).contains("복습 2개");
        assertThat(insert.body).contains("수학: 1개");
        assertThat(insert.body).contains("영어: 1개");
    }

    @Test
    @DisplayName("OFF→ON - 다시 켜면 백필로 묶음을 재생성한다(보조 캘린더는 재사용)")
    void disable_then_enable_rebackfills() throws Exception {
        connectGoogle(null, false);
        accessTokenRepository.save(memberId, "test-access", Duration.ofMinutes(30));
        seedPendingReview(today.plusDays(1));

        toggle(true).andExpect(status().isOk());
        assertThat(mirrorRepository.findAllByMemberId(memberId)).hasSize(1);

        toggle(false).andExpect(status().isOk());
        assertThat(mirrorRepository.findAllByMemberId(memberId)).isEmpty();

        REQUESTS.clear();
        toggle(true).andExpect(status().isOk());
        assertThat(mirrorRepository.findByMemberIdAndDueDate(memberId, today.plusDays(1)))
                .isPresent();
        // 보조 캘린더는 보존됐으므로 재생성(calendars.insert) 없이 재사용한다
        assertThat(REQUESTS).noneMatch(r -> r.path.equals("/calendar/v3/calendars"));
    }

    private void connectGoogle(String reviewCalendarId, boolean mirrorEnabled) {
        GoogleAccount account = new GoogleAccount(
                memberId, "refresh", "scope", "me@gmail.com", "primary", "@default");
        if (mirrorEnabled) {
            account.enableReviewMirror(reviewCalendarId);
        } else if (reviewCalendarId != null) {
            account.enableReviewMirror(reviewCalendarId);
            account.disableReviewMirror();
        }
        googleAccountRepository.save(account);
    }

    private void seedPendingReview(LocalDate dueDate) {
        seedPendingReviewIn(materialId, dueDate);
    }

    private void seedPendingReviewIn(Long material, LocalDate dueDate) {
        Flashcard card = flashcardRepository.save(new Flashcard(memberId, material, "Q", "A"));
        reviewScheduleRepository.save(new ReviewSchedule(
                memberId, card.getId(), 1, atTestZone(dueDate.atTime(4, 0)), 1, EF));
    }

    private org.springframework.test.web.servlet.ResultActions toggle(boolean enabled) throws Exception {
        return mockMvc.perform(put("/api/planner/reviews/mirror")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":" + enabled + "}"));
    }

    private static HttpServer createStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/calendar/v3/calendars", ReviewMirrorToggleIntegrationTest::handle);
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

        if ("DELETE".equals(method)) {
            respond(exchange, 204, null);
            return;
        }
        if ("POST".equals(method) && path.equals("/calendar/v3/calendars")) {
            respond(exchange, 200, "{\"id\":\"review-cal-new\"}"); // calendars.insert
            return;
        }
        if ("POST".equals(method)) {
            respond(exchange, 200, "{\"id\":\"evt-" + EVENT_SEQ.incrementAndGet() + "\"}"); // event insert
            return;
        }
        String eventId = path.substring(path.lastIndexOf('/') + 1);
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
