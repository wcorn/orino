package ds.project.orino.planner.travel.expense;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.planner.travel.tools.StubEcbRatesClient;
import ds.project.orino.planner.travel.tools.client.EcbRates;
import ds.project.orino.planner.travel.tools.client.EcbRatesClient;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.FixedClock;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.TravelCityFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 여행 경비(경비 독립 §6). <b>여행 전용 장부다</b> — 가계부 없이 적고 고치고 지운다.
 *
 * <p>여기서 지키는 것은 셋이다. 가계부였던 부분이 따라오지 않았나(상쇄·자산·승격 배치),
 * 화면이 쓰던 규칙이 그대로인가(그룹·게이지·예산), 그리고 <b>기록을 막지 않는가</b> —
 * 제목도 분류도 환율도 없이 저장되는 길이 이 기능의 핵심이다.
 *
 * <p>고정 시각은 {@code 2026-01-15}(도쿄 11:00). 여행 기간을 그 앞뒤로 놓아 예정·진행 중·완료
 * 셋을 만든다 — 상태에 따라 「하루 얼마 쓸 수 있나」와 「하루 평균」이 자리를 바꾼다.
 */
@FixedClock
class TripExpenseApiTest extends ApiTestSupport {

    /** 고시표 캐시는 통화쌍과 무관한 전역 키라 테스트 사이에 샌다. */
    private static final String FX_CACHE_KEY = "travel:fx:ecb";

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private EcbRatesClient ratesClient;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private StubEcbRatesClient ratesStub;
    private String authHeader;
    private String otherAuthHeader;
    private long osaka;
    private long kyoto;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        redisTemplate.delete(FX_CACHE_KEY);
        ratesStub = (StubEcbRatesClient) ratesClient;
        ratesStub.reset();

        memberRepository.save(MemberFixture.create());
        memberRepository.save(MemberFixture.create("other", "password"));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        otherAuthHeader = "Bearer "
                + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");

        osaka = TravelCityFixture.createCity(mockMvc, authHeader, "오사카", "Asia/Tokyo", "JPY");
        kyoto = TravelCityFixture.createCity(mockMvc, authHeader, "교토", "Asia/Tokyo", "JPY");
    }

    @Nested
    @DisplayName("적기 — 기록을 막지 않는다")
    class Writing {

        @Test
        @DisplayName("금액만 적어도 저장된다 — 제목도 분류도 없이")
        void savesWithAmountOnly() throws Exception {
            long tripId = ongoingTrip();

            create(tripId, """
                    {"occurredOn": "2026-01-15", "amount": 11300}
                    """)
                    .andExpect(jsonPath("$.data.amount").value(11300))
                    .andExpect(jsonPath("$.data.title").value(nullValue()))
                    .andExpect(jsonPath("$.data.category").value(nullValue()))
                    // 분류가 빈 건은 화면이 「정리할 내역 N건」으로 잡는다.
                    .andExpect(jsonPath("$.data.uncategorized").value(true));

            expenses(tripId).andExpect(jsonPath("$.data.unsortedCount").value(1));
        }

        @Test
        @DisplayName("분류·결제수단은 보낸 그대로 온다 — 편집 시트가 이 값으로 열린다")
        void carriesCategoryAndPaymentMethod() throws Exception {
            long tripId = ongoingTrip();

            create(tripId, """
                    {"occurredOn": "2026-01-15", "amount": 32000, "title": "이자카야",
                     "category": "FOOD", "paymentMethod": "국민 체크"}
                    """)
                    .andExpect(jsonPath("$.data.category").value("FOOD"))
                    .andExpect(jsonPath("$.data.paymentMethod").value("국민 체크"))
                    .andExpect(jsonPath("$.data.uncategorized").value(false));

            // 목록의 줄도 같은 모양이라 화면이 그 줄만 갈아 끼우면 된다.
            expenses(tripId)
                    .andExpect(jsonPath("$.data.groups[1].rows[0].category").value("FOOD"))
                    .andExpect(jsonPath("$.data.groups[1].rows[0].paymentMethod")
                            .value("국민 체크"));
        }

        @Test
        @DisplayName("금액도 외화도 없으면 400 — 빈 줄은 장부에 쓸모가 없다")
        void rejectsMissingAmount() throws Exception {
            long tripId = ongoingTrip();

            mockMvc.perform(post("/api/travel/trips/" + tripId + "/expenses")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"occurredOn": "2026-01-15", "title": "이자카야"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-027"));
        }

        @Test
        @DisplayName("0 이하도 400이다 — 금액 없는 것과 같은 자리, 같은 문구")
        void rejectsNonPositiveAmount() throws Exception {
            long tripId = ongoingTrip();

            mockMvc.perform(post("/api/travel/trips/" + tripId + "/expenses")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"occurredOn": "2026-01-15", "amount": 0}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-027"));
        }

        @Test
        @DisplayName("미래 날짜는 예정으로 저장된다 — 「예정으로 적기」를 외우게 하지 않는다")
        void futureDateBecomesScheduled() throws Exception {
            long tripId = ongoingTrip();

            create(tripId, """
                    {"occurredOn": "2026-01-17", "amount": 80000, "title": "숙소 잔금"}
                    """)
                    .andExpect(jsonPath("$.data.status").value("SCHEDULED"));

            // 게이지 2층이 이 값이다. 「썼다」에는 들어가지 않는다.
            expenses(tripId)
                    .andExpect(jsonPath("$.data.totals.spent").value(0))
                    .andExpect(jsonPath("$.data.totals.scheduled").value(80000));
        }

        @Test
        @DisplayName("status를 보내면 그 값이 이긴다 — 미리 낸 숙소비도 있다")
        void explicitStatusWins() throws Exception {
            long tripId = ongoingTrip();

            create(tripId, """
                    {"occurredOn": "2026-01-17", "amount": 80000, "status": "CONFIRMED"}
                    """)
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

            expenses(tripId).andExpect(jsonPath("$.data.totals.spent").value(80000));
        }
    }

    /**
     * 「엔으로 적고 원으로 센다」. <b>환율은 저장 시점에 굳는다</b> — 조회할 때 다시 계산하면
     * 지난 여행의 총액이 매일 바뀐다(§4.3).
     *
     * <p>스텁 고시표: 1 EUR = 182.64 JPY = 1600.00 KRW → 1 JPY ≈ 8.7604 KRW.
     */
    @Nested
    @DisplayName("외화")
    class ForeignCurrency {

        @Test
        @DisplayName("환율을 안 보내면 고시로 채우고 그 값을 지출에 고정한다")
        void fillsRateFromEcb() throws Exception {
            long tripId = ongoingTrip();

            create(tripId, """
                    {"occurredOn": "2026-01-15", "title": "이자카야",
                     "fx": {"currency": "JPY", "amount": 1200.00}}
                    """)
                    // 1200 × 8.7604 = 10512.48 → 10512
                    .andExpect(jsonPath("$.data.amount").value(10512))
                    .andExpect(jsonPath("$.data.fx.currency").value("JPY"))
                    .andExpect(jsonPath("$.data.fx.rate").value(8.7604));
        }

        @Test
        @DisplayName("다음 날 고시가 바뀌어도 총액은 그대로다 — 굳은 값을 다시 계산하지 않는다")
        void rateStaysFrozen() throws Exception {
            long tripId = ongoingTrip();
            create(tripId, """
                    {"occurredOn": "2026-01-15", "fx": {"currency": "JPY", "amount": 1200.00}}
                    """);
            expenses(tripId).andExpect(jsonPath("$.data.totals.spent").value(10512));

            // 고시표가 통째로 바뀐 상태 — 캐시까지 비워 새 값을 읽게 한다.
            redisTemplate.delete(FX_CACHE_KEY);
            ratesStub.result = Optional.of(new EcbRates(LocalDate.parse("2026-01-16"),
                    Map.of("JPY", new BigDecimal("100.00"),
                            "KRW", new BigDecimal("1600.00"))));

            expenses(tripId)
                    .andExpect(jsonPath("$.data.totals.spent").value(10512))
                    .andExpect(jsonPath("$.data.groups[1].rows[0].fx.rate").value(8.7604));
        }

        @Test
        @DisplayName("보낸 환율이 있으면 고시를 부르지 않는다")
        void honorsGivenRate() throws Exception {
            long tripId = ongoingTrip();
            int before = ratesStub.calls;

            create(tripId, """
                    {"occurredOn": "2026-01-15",
                     "fx": {"currency": "JPY", "amount": 1000.00, "rate": 9.5}}
                    """)
                    .andExpect(jsonPath("$.data.amount").value(9500));

            assertThat(ratesStub.calls).isEqualTo(before);
        }

        @Test
        @DisplayName("고시표에 없는 통화는 400 — 사용자가 고쳐야 할 값이다")
        void rejectsUnsupportedCurrency() throws Exception {
            long tripId = ongoingTrip();

            mockMvc.perform(post("/api/travel/trips/" + tripId + "/expenses")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"occurredOn": "2026-01-15",
                                     "fx": {"currency": "XYZ", "amount": 100.00}}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-028"));
        }

        @Test
        @DisplayName("ECB에 못 닿아도 400이 아니다 — 근거만 남기고 화면이 직접 입력을 받는다")
        void savesWithoutRateWhenEcbIsDown() throws Exception {
            long tripId = ongoingTrip();
            redisTemplate.delete(FX_CACHE_KEY);
            ratesStub.result = Optional.empty();

            long expenseId = createId(tripId, """
                    {"occurredOn": "2026-01-15", "fx": {"currency": "JPY", "amount": 1200.00}}
                    """);

            // 환율 때문에 여행 중에 기록이 막히면 안 적게 된다.
            expenses(tripId)
                    .andExpect(jsonPath("$.data.groups[1].rows[0].fx.rate").value(nullValue()))
                    // 0이라 합계를 부풀리지 않는다 — 화면이 그 자리에 입력 칸을 연다.
                    .andExpect(jsonPath("$.data.totals.spent").value(0));

            // 사용자가 환율을 채우면 그때 금액이 선다.
            patchExpense(tripId, expenseId, """
                    {"fx": {"currency": "JPY", "amount": 1200.00, "rate": 9.0}}
                    """)
                    .andExpect(jsonPath("$.data.amount").value(10800));
        }
    }

    @Nested
    @DisplayName("고치기")
    class Updating {

        @Test
        @DisplayName("보낸 것만 바뀐다 — 빠진 필드는 건드리지 않는다")
        void changesOnlyWhatWasSent() throws Exception {
            long tripId = ongoingTrip();
            long expenseId = createId(tripId, """
                    {"occurredOn": "2026-01-15", "amount": 32000, "title": "이자카야",
                     "category": "FOOD", "paymentMethod": "국민 체크"}
                    """);

            patchExpense(tripId, expenseId, """
                    {"title": "이자카야 2차"}
                    """)
                    .andExpect(jsonPath("$.data.title").value("이자카야 2차"))
                    .andExpect(jsonPath("$.data.amount").value(32000))
                    .andExpect(jsonPath("$.data.category").value("FOOD"))
                    .andExpect(jsonPath("$.data.paymentMethod").value("국민 체크"));
        }

        @Test
        @DisplayName("비우려면 clear를 쓴다 — null은 「안 보냈다」와 구분되지 않는다")
        void clearsWithExplicitFlag() throws Exception {
            long tripId = ongoingTrip();
            long expenseId = createId(tripId, """
                    {"occurredOn": "2026-01-15", "amount": 32000, "title": "이자카야",
                     "category": "FOOD", "paymentMethod": "국민 체크"}
                    """);

            patchExpense(tripId, expenseId, """
                    {"clearCategory": true, "clearPaymentMethod": true}
                    """)
                    .andExpect(jsonPath("$.data.category").value(nullValue()))
                    .andExpect(jsonPath("$.data.paymentMethod").value(nullValue()))
                    .andExpect(jsonPath("$.data.uncategorized").value(true));
        }

        @Test
        @DisplayName("clearFx는 금액을 함께 받는다 — 근거 없이 숫자만 남기지 않는다")
        void clearFxNeedsAmount() throws Exception {
            long tripId = ongoingTrip();
            long expenseId = createId(tripId, """
                    {"occurredOn": "2026-01-15", "fx": {"currency": "JPY", "amount": 1200.00}}
                    """);

            mockMvc.perform(patch("/api/travel/trips/%d/expenses/%d".formatted(tripId, expenseId))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"clearFx\": true}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-027"));

            patchExpense(tripId, expenseId, """
                    {"clearFx": true, "amount": 10000}
                    """)
                    .andExpect(jsonPath("$.data.amount").value(10000))
                    .andExpect(jsonPath("$.data.fx").value(nullValue()));
        }

        @Test
        @DisplayName("날짜가 지난 예정에 overdue가 서고, 「확정」이 그걸 내린다")
        void overdueIsConfirmedByHand() throws Exception {
            // 오늘은 1/15다. 1/14에 잡아 둔 예정은 지났다.
            long tripId = ongoingTrip();
            long expenseId = createId(tripId, """
                    {"occurredOn": "2026-01-14", "amount": 80000, "title": "숙소 잔금",
                     "status": "SCHEDULED"}
                    """);

            // 승격 배치를 만들지 않았다 — 실제 결제 여부를 앱이 알 수 없다(§4.3).
            expenses(tripId)
                    .andExpect(jsonPath("$.data.groups[0].rows[0].overdue").value(true))
                    .andExpect(jsonPath("$.data.totals.spent").value(0))
                    .andExpect(jsonPath("$.data.totals.scheduled").value(80000));

            patchExpense(tripId, expenseId, "{\"status\": \"CONFIRMED\"}")
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.data.overdue").value(false));

            expenses(tripId)
                    .andExpect(jsonPath("$.data.totals.spent").value(80000))
                    .andExpect(jsonPath("$.data.totals.scheduled").value(0));
        }

        @Test
        @DisplayName("확정은 지나지 않는다 — 지난 날짜여도 overdue가 아니다")
        void confirmedIsNeverOverdue() throws Exception {
            long tripId = ongoingTrip();
            create(tripId, """
                    {"occurredOn": "2026-01-14", "amount": 32000}
                    """)
                    .andExpect(jsonPath("$.data.overdue").value(false));
        }

        @Test
        @DisplayName("다른 여행의 지출 id를 이 여행 경로로 보내면 404다")
        void rejectsExpenseOfAnotherTrip() throws Exception {
            long tripId = ongoingTrip();
            long otherTripId = completedTrip();
            long expenseId = createId(otherTripId, """
                    {"occurredOn": "2026-01-06", "amount": 32000}
                    """);

            // 경로가 말하는 것과 고쳐지는 것이 다르면 화면을 믿을 수 없다.
            mockMvc.perform(patch("/api/travel/trips/%d/expenses/%d".formatted(tripId, expenseId))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\": \"바꿔치기\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-026"));
        }

        @Test
        @DisplayName("남의 지출은 404 — 403이면 그 id가 있다는 것이 새어나간다")
        void hidesOthersExpense() throws Exception {
            long tripId = ongoingTrip();
            long expenseId = createId(tripId, """
                    {"occurredOn": "2026-01-15", "amount": 32000}
                    """);

            mockMvc.perform(patch("/api/travel/trips/%d/expenses/%d".formatted(tripId, expenseId))
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\": \"바꿔치기\"}"))
                    .andExpect(status().isNotFound())
                    // 여행 소유부터 걸린다 — 지출까지 가지도 않는다.
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-001"));
        }
    }

    @Nested
    @DisplayName("지우기 — 상쇄하지 않는다")
    class Deleting {

        @Test
        @DisplayName("지우면 목록과 합계가 함께 줄어든다 — 반대 거래를 만들지 않는다")
        void removesRowAndTotal() throws Exception {
            long tripId = ongoingTrip();
            long expenseId = createId(tripId, """
                    {"occurredOn": "2026-01-15", "amount": 32000, "title": "이자카야"}
                    """);
            create(tripId, """
                    {"occurredOn": "2026-01-15", "amount": 11300, "title": "점심 라멘"}
                    """);

            mockMvc.perform(delete("/api/travel/trips/%d/expenses/%d".formatted(tripId, expenseId))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            // 환불 거래가 남으면 목록과 합계가 다른 이야기를 한다(§4.4).
            expenses(tripId)
                    .andExpect(jsonPath("$.data.totals.spent").value(11300))
                    .andExpect(jsonPath("$.data.groups[1].rows", hasSize(1)))
                    .andExpect(jsonPath("$.data.groups[1].rows[0].title").value("점심 라멘"));
        }

        @Test
        @DisplayName("여행을 지우면 그 여행의 경비도 사라진다 — CASCADE다")
        void cascadesWithTrip() throws Exception {
            long tripId = ongoingTrip();
            create(tripId, """
                    {"occurredOn": "2026-01-15", "amount": 32000}
                    """);

            mockMvc.perform(delete("/api/travel/trips/" + tripId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            // 남을 원장이 없어졌다 — 원장 쪽 FK가 SET NULL이던 것과 반대다.
            assertThat(countExpenses(tripId)).isZero();
        }
    }

    @Nested
    @DisplayName("어떻게 묶나")
    class Grouping {

        @Test
        @DisplayName("출발 전 결제는 「출발 전」으로 묶인다 — 항공권을 빼면 총액이 설명되지 않는다")
        void groupsBeforeDeparture() throws Exception {
            long tripId = ongoingTrip();
            expense(tripId, 640000, "항공권", "2025-11-30");
            expense(tripId, 32000, "이자카야", "2026-01-15");

            expenses(tripId)
                    .andExpect(jsonPath("$.data.groups[0].key").value("BEFORE"))
                    .andExpect(jsonPath("$.data.groups[0].label").value("출발 전"))
                    .andExpect(jsonPath("$.data.groups[0].sum").value(640000));
        }

        @Test
        @DisplayName("돌아온 뒤 결제는 「다녀온 뒤」로 같은 자리에 붙는다")
        void groupsAfterReturn() throws Exception {
            long tripId = completedTrip();
            expense(tripId, 18000, "현상 인화", "2026-01-12");

            expenses(tripId)
                    .andExpect(jsonPath("$.data.groups[-1:].key").value("AFTER"))
                    .andExpect(jsonPath("$.data.groups[-1:].sum").value(18000));
        }

        @Test
        @DisplayName("비어 있으면 출발 전·다녀온 뒤는 아예 내리지 않는다")
        void skipsEmptyEdgeGroups() throws Exception {
            long tripId = ongoingTrip();
            expense(tripId, 32000, "이자카야", "2026-01-15");

            // 늘 보이면 여행 중 화면의 위아래가 빈 카드로 찬다.
            expenses(tripId)
                    .andExpect(jsonPath("$.data.groups[?(@.key == 'BEFORE')]", hasSize(0)))
                    .andExpect(jsonPath("$.data.groups[?(@.key == 'AFTER')]", hasSize(0)))
                    .andExpect(jsonPath("$.data.groups", hasSize(3)));
        }

        @Test
        @DisplayName("지출이 없는 날짜도 sum 0으로 내려간다 — 화면이 「아직 없어요」를 그린다")
        void keepsEmptyDayGroups() throws Exception {
            long tripId = ongoingTrip();

            expenses(tripId)
                    .andExpect(jsonPath("$.data.groups", hasSize(3)))
                    .andExpect(jsonPath("$.data.groups[0].key").value("DAY-1"))
                    .andExpect(jsonPath("$.data.groups[0].sum").value(0))
                    .andExpect(jsonPath("$.data.groups[0].rows", hasSize(0)));
        }

        @Test
        @DisplayName("라벨은 저장하지 않는다 — 기준 도시를 바꾸면 따라 움직인다")
        void labelFollowsBaseCity() throws Exception {
            long tripId = ongoingTrip();
            expense(tripId, 32000, "이자카야", "2026-01-15");

            expenses(tripId)
                    .andExpect(jsonPath("$.data.groups[1].label").value("1.15 (목) · 오사카"));

            changeBaseCity(dayIdOf(tripId, 1), kyoto);

            // 저장했다면 옛 도시가 조용히 남았을 자리다.
            expenses(tripId)
                    .andExpect(jsonPath("$.data.groups[1].label").value("1.15 (목) · 교토"))
                    .andExpect(jsonPath("$.data.groups[1].cityName").value("교토"));
        }
    }

    @Nested
    @DisplayName("예산")
    class Budget {

        @Test
        @DisplayName("안 정했으면 budget이 통째로 null이다 — 0을 내리지 않는다")
        void nullWhenUnset() throws Exception {
            long tripId = ongoingTrip();
            expense(tripId, 32000, "이자카야", "2026-01-15");

            // amount: 0을 내리면 화면이 「0원 중 3.2만」을 그린다(§5.3).
            expenses(tripId)
                    .andExpect(jsonPath("$.data.budget").value(nullValue()))
                    // 「얼마 썼나」는 예산 없이도 답이 있다.
                    .andExpect(jsonPath("$.data.totals.spent").value(32000));
        }

        @Test
        @DisplayName("정하면 남은 돈과 하루 쓸 수 있는 돈이 함께 온다")
        void derivesDailyAllowance() throws Exception {
            long tripId = ongoingTrip();
            putBudget(tripId, "800000").andExpect(jsonPath("$.data.amount").value(800000));
            expense(tripId, 200000, "이자카야", "2026-01-15");

            // 오늘이 2일차, 기간은 1/14~1/16이라 남은 날은 오늘 포함 2일이다.
            expenses(tripId)
                    .andExpect(jsonPath("$.data.budget.amount").value(800000))
                    .andExpect(jsonPath("$.data.budget.spent").value(200000))
                    .andExpect(jsonPath("$.data.budget.remaining").value(600000))
                    .andExpect(jsonPath("$.data.budget.daysLeft").value(2))
                    .andExpect(jsonPath("$.data.budget.dailyAllowance").value(300000));
        }

        @Test
        @DisplayName("다녀온 뒤에는 하루 평균이 그 자리를 받는다 — 둘이 동시에 차지 않는다")
        void averageReplacesAllowanceAfterTrip() throws Exception {
            long tripId = completedTrip();
            putBudget(tripId, "800000");
            expense(tripId, 900000, "이자카야", "2026-01-06");

            expenses(tripId)
                    .andExpect(jsonPath("$.data.budget.dailyAllowance").value(nullValue()))
                    .andExpect(jsonPath("$.data.budget.daysLeft").value(nullValue()))
                    // 총 3일 · 90만이면 하루 평균 30만.
                    .andExpect(jsonPath("$.data.totals.dailyAverage").value(300000));
        }

        @Test
        @DisplayName("예산을 넘겨도 하루 쓸 수 있는 돈이 음수가 되지는 않는다")
        void allowanceNeverGoesNegative() throws Exception {
            long tripId = ongoingTrip();
            putBudget(tripId, "100000");
            expense(tripId, 300000, "이자카야", "2026-01-15");

            expenses(tripId)
                    // 남은 돈은 사실대로 음수다 — 초과했다는 것이 그 값의 내용이다.
                    .andExpect(jsonPath("$.data.budget.remaining").value(-200000))
                    // 그러나 「하루 −10만 쓸 수 있다」는 말이 안 된다.
                    .andExpect(jsonPath("$.data.budget.dailyAllowance").value(0));
        }

        @Test
        @DisplayName("0은 400이다 — 「안 정함」과 구분되지 않는다")
        void rejectsZero() throws Exception {
            long tripId = ongoingTrip();

            mockMvc.perform(put("/api/travel/trips/" + tripId + "/budget")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\": 0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-024"));
        }

        @Test
        @DisplayName("null이 해제다")
        void nullClearsBudget() throws Exception {
            long tripId = ongoingTrip();
            putBudget(tripId, "800000");

            putBudget(tripId, "null").andExpect(jsonPath("$.data.amount").value(nullValue()));

            expenses(tripId).andExpect(jsonPath("$.data.budget").value(nullValue()));
        }
    }

    /**
     * 사이드바 여행 트리의 경비 한 줄(#1345 · API §2.1). 여기서 지키는 것은 <b>화면과 같은
     * 값인가</b> 하나다 — 사이드바를 보고 들어간 사람이 경비 화면에서 다른 숫자를 보면,
     * 어느 쪽이 맞는지 알 방법이 없다.
     */
    @Nested
    @DisplayName("사이드바 요약 — trips[].expense")
    class SidebarExpense {

        @Test
        @DisplayName("사이드바의 spent가 경비 화면의 「썼다」와 같은 값이다")
        void spentMatchesExpenseScreen() throws Exception {
            long tripId = ongoingTrip();
            expense(tripId, 32000, "이자카야", "2026-01-15");
            expense(tripId, 11300, "점심 라멘", "2026-01-15");
            // 아직 안 나간 돈은 「썼다」가 아니다 — 화면도 여기도 예정은 빼고 센다.
            expense(tripId, 80000, "숙소 잔금", "2026-01-17");

            expenses(tripId).andExpect(jsonPath("$.data.totals.spent").value(43300));

            summary().andExpect(jsonPath("$.data.trips[0].expense.spent").value(43300));
        }

        @Test
        @DisplayName("예산을 정했으면 그대로, 안 정했으면 null이다 — 0을 내리지 않는다")
        void budgetIsNullWhenUnset() throws Exception {
            long tripId = ongoingTrip();

            summary().andExpect(jsonPath("$.data.trips[0].expense.budget").value(nullValue()));

            putBudget(tripId, "800000");

            summary().andExpect(jsonPath("$.data.trips[0].expense.budget").value(800000));
        }

        @Test
        @DisplayName("한 푼도 안 쓴 여행도 expense가 온다 — spent가 0이지 null이 아니다")
        void spentIsZeroNotNull() throws Exception {
            ongoingTrip();

            summary()
                    .andExpect(jsonPath("$.data.trips[0].expense").exists())
                    .andExpect(jsonPath("$.data.trips[0].expense.spent").value(0))
                    .andExpect(jsonPath("$.data.trips[0].expense.budget").value(nullValue()));
        }

        private ResultActions summary() throws Exception {
            return mockMvc.perform(get("/api/travel/summary")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("남의 여행은 404 — 조회도 입력도 예산도")
    void hidesOthersTrip() throws Exception {
        long tripId = ongoingTrip();

        mockMvc.perform(get("/api/travel/trips/" + tripId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, otherAuthHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRAVEL-ERR-001"));
        mockMvc.perform(post("/api/travel/trips/" + tripId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, otherAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"occurredOn\": \"2026-01-15\", \"amount\": 1000}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/travel/trips/" + tripId + "/budget")
                        .header(HttpHeaders.AUTHORIZATION, otherAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 800000}"))
                .andExpect(status().isNotFound());
    }

    // ---------------- helpers ----------------

    /** 오늘(1/15)이 2일차인 여행. */
    private long ongoingTrip() throws Exception {
        return createTrip("2026-01-14", "2026-01-16");
    }

    /** 이미 끝난 여행 — 「하루 평균」이 채워지는 상태. */
    private long completedTrip() throws Exception {
        return createTrip("2026-01-05", "2026-01-07");
    }

    private long createTrip(String start, String end) throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "일본", "startDate": "%s", "endDate": "%s", %s}
                                """.formatted(start, end,
                                TravelCityFixture.singleLeg(osaka, 3))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private ResultActions create(long tripId, String json) throws Exception {
        return mockMvc.perform(post("/api/travel/trips/" + tripId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    private long createId(long tripId, String json) throws Exception {
        String body = create(tripId, json).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.expenseId")).longValue();
    }

    private ResultActions patchExpense(long tripId, long expenseId, String json)
            throws Exception {
        return mockMvc.perform(patch("/api/travel/trips/%d/expenses/%d"
                        .formatted(tripId, expenseId))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    private void expense(long tripId, long amount, String title, String date) throws Exception {
        create(tripId, """
                {"occurredOn": "%s", "amount": %d, "title": "%s"}
                """.formatted(date, amount, title));
    }

    private ResultActions expenses(long tripId) throws Exception {
        return mockMvc.perform(get("/api/travel/trips/" + tripId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }

    private ResultActions putBudget(long tripId, String amount) throws Exception {
        return mockMvc.perform(put("/api/travel/trips/" + tripId + "/budget")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": %s}".formatted(amount)))
                .andExpect(status().isOk());
    }

    private Integer countExpenses(long tripId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trip_expense WHERE trip_id = ?", Integer.class, tripId);
    }

    private long dayIdOf(long tripId, int index) throws Exception {
        String body = mockMvc.perform(get("/api/travel/trips/" + tripId + "/days")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data[%d].dayId".formatted(index))).longValue();
    }

    private void changeBaseCity(long dayId, long cityPlaceId) throws Exception {
        mockMvc.perform(put("/api/travel/days/" + dayId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseCityPlaceId\": %d}".formatted(cityPlaceId)))
                .andExpect(status().isOk());
    }
}
