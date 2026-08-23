package ds.project.orino.planner.shortlink;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.shortlink.entity.ShortlinkVisit;
import ds.project.orino.domain.planner.shortlink.repository.ShortlinkRepository;
import ds.project.orino.domain.planner.shortlink.repository.ShortlinkVisitDailyRepository;
import ds.project.orino.domain.planner.shortlink.repository.ShortlinkVisitRepository;
import ds.project.orino.planner.shortlink.visit.VisitContext;
import ds.project.orino.planner.shortlink.visit.VisitRecorder;
import ds.project.orino.planner.shortlink.visit.VisitRetentionScheduler;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 방문 통계(#1240).
 *
 * <p>기록은 비동기라 <b>결과를 기다렸다 확인한다</b>. 대기 자체가 이 기능의 성질이다 —
 * 리다이렉트는 기록을 기다리지 않고 나간다(명세 §6.5).
 */
class VisitStatsTest extends ApiTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String TARGET = "https://img.orino.dev/note-images/2026/aug.jpg";
    private static final String IPHONE =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Mobile/15E148 Safari/604.1";
    private static final String KAKAO_BOT = "kakaotalk-scrap/1.0";

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ShortlinkRepository shortlinkRepository;
    @Autowired
    private ShortlinkVisitRepository visitRepository;
    @Autowired
    private ShortlinkVisitDailyRepository dailyRepository;
    @Autowired
    private VisitRecorder visitRecorder;
    @Autowired
    private VisitRetentionScheduler retentionScheduler;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private Clock clock;

    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        issue("jeju");
    }

    @Nested
    @DisplayName("기록")
    class Recording {

        @Test
        @DisplayName("사람 방문은 방문 수에, 봇은 봇 수에 들어간다 — 같이 세면 몇 배가 된다")
        void countsBotsSeparately() throws Exception {
            visit(IPHONE, null);
            visit(IPHONE, null);
            visit(KAKAO_BOT, null);

            waitFor(() -> visitRepository.count() == 3);

            stats().andExpect(jsonPath("$.data.totalVisits").value(2))
                    .andExpect(jsonPath("$.data.botVisits").value(1));
        }

        @Test
        @DisplayName("봇에게도 302는 정상으로 내준다 — 프리뷰 카드가 떠야 한다")
        void redirectsBotsToo() throws Exception {
            visit(KAKAO_BOT, null).andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION, TARGET));
        }

        @Test
        @DisplayName("리퍼러 전체 URL을 줘도 도메인만 저장된다")
        void storesReferrerDomainOnly() throws Exception {
            visit(IPHONE, "https://mail.google.com/mail/u/0/?tab=rm#inbox/FMfcgz");

            waitFor(() -> visitRepository.count() == 1);

            ShortlinkVisit stored = visitRepository.findAll().get(0);
            assertThat(stored.getReferrerDomain()).isEqualTo("mail.google.com");
            // 국가는 판정 수단(#1241)이 붙기 전까지 비어 있다. IP는 어디에도 없다.
            assertThat(stored.getCountry()).isNull();
        }

        @Test
        @DisplayName("같은 날 두 번 방문하면 일별 집계가 2가 된다 — 읽고 쓰지 않고 UPSERT다")
        void accumulatesDailyCount() throws Exception {
            visit(IPHONE, null);
            visit(IPHONE, null);

            waitFor(() -> visitRepository.count() == 2);

            LocalDate today = LocalDate.ofInstant(clock.instant(), KST);
            var daily = dailyRepository
                    .findAllByShortlinkIdAndVisitDateBetweenOrderByVisitDateAsc(
                            shortlinkId(), today, today);
            assertThat(daily).hasSize(1);
            assertThat(daily.get(0).getVisitCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("기록이 실패해도 예외가 새어 나가지 않는다 — 302를 막을 자리가 없다")
        void swallowsRecordingFailure() throws Exception {
            // 없는 링크에 기록을 시도하면 FK 위반이다. 그래도 호출자에게는 아무 일도 없다.
            assertThatCode(() -> visitRecorder.record(999_999L,
                    new VisitContext(IPHONE, null, clock.instant())))
                    .doesNotThrowAnyException();

            // 그 뒤에도 리다이렉트는 정상이다.
            visit(IPHONE, null).andExpect(status().isFound());
        }

        @Test
        @DisplayName("404로 끝난 요청은 방문이 아니다")
        void doesNotCountFailedVisits() throws Exception {
            mockMvc.perform(get("/r/nnnnn")).andExpect(status().isNotFound());

            waitFor(() -> true);
            assertThat(visitRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("조회")
    class Query {

        @Test
        @DisplayName("일별은 빈 날을 0으로 채워 범위 전체를 준다")
        void fillsGapsInDaily() throws Exception {
            visit(IPHONE, null);
            waitFor(() -> visitRepository.count() == 1);

            stats("7d").andExpect(jsonPath("$.data.daily", hasSize(7)))
                    // 마지막 칸이 오늘이고, 오늘 방문이 1이다.
                    .andExpect(jsonPath("$.data.daily[6].count").value(1))
                    .andExpect(jsonPath("$.data.daily[0].count").value(0));
        }

        @Test
        @DisplayName("기기 비율과 유입 경로가 사람 방문만으로 계산된다")
        void summarizesHumanVisitsOnly() throws Exception {
            visit(IPHONE, "https://mail.google.com/mail");
            visit(KAKAO_BOT, "https://kakao.com/talk");
            waitFor(() -> visitRepository.count() == 2);

            stats().andExpect(jsonPath("$.data.devices", hasSize(1)))
                    .andExpect(jsonPath("$.data.devices[0].device").value("MOBILE"))
                    .andExpect(jsonPath("$.data.devices[0].ratio").value(1.0))
                    .andExpect(jsonPath("$.data.referrers", hasSize(1)))
                    .andExpect(jsonPath("$.data.referrers[0].domain").value("mail.google.com"));
        }

        @Test
        @DisplayName("목록과 요약에도 실제 방문 수가 실린다")
        void showsVisitsInListAndSummary() throws Exception {
            visit(IPHONE, null);
            waitFor(() -> visitRepository.count() == 1);

            mockMvc.perform(get("/api/shortlinks")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.recent[0].visitCount").value(1))
                    .andExpect(jsonPath("$.data.recent[0].lastVisitedAt").isNotEmpty());
            mockMvc.perform(get("/api/shortlinks/summary")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.visitsThisWeek").value(1));
        }

        @Test
        @DisplayName("남의 링크 통계는 404다 — 통계가 존재를 알려주는 창구가 되면 안 된다")
        void hidesOtherMembersStats() throws Exception {
            memberRepository.save(MemberFixture.create("other", "password"));
            String otherHeader = "Bearer "
                    + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");

            mockMvc.perform(get("/api/shortlinks/jeju/stats")
                            .header(HttpHeaders.AUTHORIZATION, otherHeader))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SL-ERR-006"));
        }
    }

    @Nested
    @DisplayName("90일 정리")
    class Retention {

        @Test
        @DisplayName("오래된 원시만 지우고 집계는 건드리지 않는다")
        void purgesRawKeepsDaily() throws Exception {
            visit(IPHONE, null);
            waitFor(() -> visitRepository.count() == 1);

            // 91일 전으로 옮겨 심는다 — 시간을 되돌리는 대신 데이터를 옮긴다.
            Instant old = clock.instant().minus(Duration.ofDays(91));
            visitRepository.save(new ShortlinkVisit(shortlinkId(), old, "old.example.com",
                    null, null, false));
            assertThat(visitRepository.count()).isEqualTo(2);

            retentionScheduler.purgeOldVisits();

            assertThat(visitRepository.count()).isEqualTo(1);
            // 총 방문은 집계에서 나오므로 그대로다 — 원시가 지워져도 그래프는 남는다.
            stats().andExpect(jsonPath("$.data.totalVisits").value(1));
            LocalDate today = LocalDate.ofInstant(clock.instant(), KST);
            assertThat(dailyRepository
                    .findAllByShortlinkIdAndVisitDateBetweenOrderByVisitDateAsc(
                            shortlinkId(), today, today))
                    .hasSize(1);
        }
    }

    private Long shortlinkId() {
        return shortlinkRepository.findBySlug("jeju").orElseThrow().getId();
    }

    private ResultActions visit(String userAgent, String referer) throws Exception {
        MockHttpServletRequestBuilder request =
                get("/r/jeju").header(HttpHeaders.USER_AGENT, userAgent);
        if (referer != null) {
            request = request.header(HttpHeaders.REFERER, referer);
        }
        return mockMvc.perform(request);
    }

    private ResultActions stats() throws Exception {
        return stats("30d");
    }

    private ResultActions stats(String range) throws Exception {
        return mockMvc.perform(get("/api/shortlinks/jeju/stats")
                        .param("range", range)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }

    private void issue(String slug) throws Exception {
        mockMvc.perform(post("/api/shortlinks")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetUrl": "%s", "slug": "%s"}
                                """.formatted(TARGET, slug)))
                .andExpect(status().isOk());
    }

    /**
     * 비동기 기록이 끝나기를 기다린다. 조건이 참이 될 때까지 짧게 재시도한다 —
     * 고정된 sleep은 느린 CI에서 깨지고 빠른 로컬에서 낭비다.
     */
    private void waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                // 조건 직후에도 같은 트랜잭션이 커밋 중일 수 있어 한 박자 둔다.
                Thread.sleep(50);
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("비동기 방문 기록이 5초 안에 끝나지 않았다");
    }
}
