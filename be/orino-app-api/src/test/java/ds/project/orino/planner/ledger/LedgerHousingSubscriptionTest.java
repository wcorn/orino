package ds.project.orino.planner.ledger;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.FixedClock;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
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
 * 청약 인정 현황(#1389).
 *
 * <p>오늘은 2026-09-13이다. 이번 달(9월)은 끝나지 않았으므로 입금이 없어도 「입금 없음」이
 * 붙지 않는다 — 날짜 경계를 보는 테스트라 시각을 못박는다.
 */
@FixedClock("2026-09-13T03:00:00Z")
class LedgerHousingSubscriptionTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private long checking;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        checking = LedgerFixture.createAsset(mockMvc, authHeader, "급여통장", "CHECKING");
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "INCOME", "amount": 50000000, "assetId": %d, "occurredOn": "2024-01-02"}
                """.formatted(checking));
    }

    @Nested
    @DisplayName("종류")
    class Kind {

        @Test
        @DisplayName("예·적금이 아닌 자산에는 청약을 붙일 수 없다")
        void rejectsNonSavingsOnCreate() throws Exception {
            mockMvc.perform(post("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "급여통장2", "type": "CHECKING",
                                     "savingsKind": "HOUSING_SUBSCRIPTION"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-040"));
        }

        @Test
        @DisplayName("수정으로도 입출금에 청약을 붙일 수 없다")
        void rejectsNonSavingsOnUpdate() throws Exception {
            patchAsset(checking, "{\"savingsKind\": \"HOUSING_SUBSCRIPTION\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-040"));
        }

        @Test
        @DisplayName("이미 만든 예·적금을 청약으로 바꾸고 다시 일반으로 되돌린다 — 유형과 달리 종류는 바뀐다")
        void changesKindOfExistingSavings() throws Exception {
            long savings = LedgerFixture.createAsset(mockMvc, authHeader, "주택청약", "SAVINGS");

            patchAsset(savings, "{\"savingsKind\": \"HOUSING_SUBSCRIPTION\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.savingsKind").value("HOUSING_SUBSCRIPTION"));

            patchAsset(savings, "{\"clearSavingsKind\": true}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.savingsKind").value(nullValue()));
        }

        @Test
        @DisplayName("일반 예·적금에는 인정 현황이 없다")
        void plainSavingsHasNoSubscription() throws Exception {
            long savings = LedgerFixture.createAsset(mockMvc, authHeader, "비상금", "SAVINGS");

            mockMvc.perform(get("/api/ledger/assets/%d/subscription".formatted(savings))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-040"));
        }
    }

    @Nested
    @DisplayName("기준값이 없을 때")
    class WithoutBaseline {

        @Test
        @DisplayName("추정을 그리지 않는다 — 가입일부터 센 틀린 숫자보다 빈칸이 낫다")
        void drawsNothing() throws Exception {
            long subscription = subscription();
            deposit(subscription, 250000, "2026-08-25");

            getSubscription(subscription)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.baseline").value(nullValue()))
                    .andExpect(jsonPath("$.data.estimate").value(nullValue()))
                    .andExpect(jsonPath("$.data.months").isEmpty())
                    .andExpect(jsonPath("$.data.monthlyCap").value(250000));

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.groups[0].assets[?(@.name=='주택청약')].subscriptionCount")
                            .value(contains(nullValue())));
        }
    }

    @Nested
    @DisplayName("인정 추정")
    class Estimate {

        @Test
        @DisplayName("기준 월 다음 달부터 달마다 1회, 상한까지 센다")
        void countsFromMonthAfterBaseline() throws Exception {
            long subscription = subscription();
            baseline(subscription, 38, 5300000, "2026-06").andExpect(status().isOk());
            // 기준 월의 입금은 이미 청약홈 값에 들어 있다.
            deposit(subscription, 250000, "2026-06-25");
            deposit(subscription, 250000, "2026-07-25");
            deposit(subscription, 250000, "2026-08-10");
            deposit(subscription, 250000, "2026-08-25");

            getSubscription(subscription)
                    .andExpect(jsonPath("$.data.baseline.count").value(38))
                    .andExpect(jsonPath("$.data.baseline.throughMonth").value("2026-06"))
                    .andExpect(jsonPath("$.data.estimate.count").value(40))
                    .andExpect(jsonPath("$.data.estimate.amount").value(5800000))
                    .andExpect(jsonPath("$.data.estimate.isEstimate").value(true))
                    .andExpect(jsonPath("$.data.months", hasSize(3)))
                    .andExpect(jsonPath("$.data.months[0].month").value("2026-07"))
                    .andExpect(jsonPath("$.data.months[0].flag").value(nullValue()))
                    .andExpect(jsonPath("$.data.months[1].deposited").value(500000))
                    .andExpect(jsonPath("$.data.months[1].recognized").value(250000))
                    .andExpect(jsonPath("$.data.months[1].flag").value("OVER_CAP"))
                    // 이번 달은 약정일이 아직 안 왔을 수 있다.
                    .andExpect(jsonPath("$.data.months[2].month").value("2026-09"))
                    .andExpect(jsonPath("$.data.months[2].deposited").value(0))
                    .andExpect(jsonPath("$.data.months[2].flag").value(nullValue()));
        }

        @Test
        @DisplayName("끝난 달에 입금이 없으면 「입금 없음」 행으로 남긴다")
        void marksEndedMonthWithoutDeposit() throws Exception {
            long subscription = subscription();
            baseline(subscription, 10, 2500000, "2026-06");
            deposit(subscription, 250000, "2026-08-25");

            getSubscription(subscription)
                    .andExpect(jsonPath("$.data.months[0].month").value("2026-07"))
                    .andExpect(jsonPath("$.data.months[0].flag").value("NO_DEPOSIT"))
                    .andExpect(jsonPath("$.data.estimate.count").value(11));
        }

        @Test
        @DisplayName("2024년 10월분까지는 10만 원까지만 센다")
        void lowerCapUntilOctober2024() throws Exception {
            long subscription = subscription();
            baseline(subscription, 0, 0, "2024-09");
            deposit(subscription, 150000, "2024-10-25");
            deposit(subscription, 150000, "2024-11-25");

            getSubscription(subscription)
                    .andExpect(jsonPath("$.data.months[0].recognized").value(100000))
                    .andExpect(jsonPath("$.data.months[0].flag").value("OVER_CAP"))
                    .andExpect(jsonPath("$.data.months[1].recognized").value(150000))
                    .andExpect(jsonPath("$.data.months[1].flag").value(nullValue()))
                    .andExpect(jsonPath("$.data.estimate.amount").value(250000));
        }

        @Test
        @DisplayName("확정 이체만 센다 — 이자 수입 · 지운 이체 · 예정 이체는 빠진다")
        void countsOnlyConfirmedTransfers() throws Exception {
            long subscription = subscription();
            baseline(subscription, 0, 0, "2026-07");
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "INCOME", "amount": 3000, "assetId": %d, "occurredOn": "2026-08-20"}
                    """.formatted(subscription));
            long removed = LedgerFixture.transactionId(deposit(subscription, 250000, "2026-08-25"));
            mockMvc.perform(delete("/api/ledger/transactions/" + removed)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());
            // 미래 날짜라 예정으로 저장된다.
            deposit(subscription, 250000, "2026-09-25");

            getSubscription(subscription)
                    .andExpect(jsonPath("$.data.estimate.count").value(0))
                    .andExpect(jsonPath("$.data.estimate.amount").value(0))
                    .andExpect(jsonPath("$.data.months[0].flag").value("NO_DEPOSIT"));
        }

        @Test
        @DisplayName("자산 목록에 인정 회차 추정이 붙는다")
        void listCarriesEstimatedCount() throws Exception {
            long subscription = subscription();
            baseline(subscription, 38, 5300000, "2026-08");
            deposit(subscription, 250000, "2026-09-05");

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.groups[0].assets[?(@.name=='주택청약')].savingsKind")
                            .value(contains("HOUSING_SUBSCRIPTION")))
                    .andExpect(jsonPath("$.data.groups[0].assets[?(@.name=='주택청약')].subscriptionCount")
                            .value(contains(39)));
        }
    }

    @Nested
    @DisplayName("기준값 다시 맞추기")
    class Rebaseline {

        @Test
        @DisplayName("처음 적으면 직전 추정이 없다")
        void firstBaselineHasNoBefore() throws Exception {
            long subscription = subscription();

            baseline(subscription, 38, 5300000, "2026-09")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.before").value(nullValue()))
                    .andExpect(jsonPath("$.data.after.count").value(38))
                    .andExpect(jsonPath("$.data.after.amount").value(5300000));
        }

        @Test
        @DisplayName("다시 맞추면 바꾸기 직전의 추정을 함께 돌려준다 — 어긋남을 감추지 않는다")
        void returnsEstimateBeforeChange() throws Exception {
            long subscription = subscription();
            baseline(subscription, 38, 5300000, "2026-06");
            deposit(subscription, 250000, "2026-07-25");
            deposit(subscription, 250000, "2026-08-25");

            baseline(subscription, 41, 6050000, "2026-09")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.before.count").value(40))
                    .andExpect(jsonPath("$.data.before.amount").value(5800000))
                    .andExpect(jsonPath("$.data.after.count").value(41))
                    .andExpect(jsonPath("$.data.after.amount").value(6050000));

            getSubscription(subscription)
                    .andExpect(jsonPath("$.data.baseline.throughMonth").value("2026-09"))
                    .andExpect(jsonPath("$.data.months").isEmpty());
        }

        @Test
        @DisplayName("셋 중 일부만 오면 거부한다 — 어디서부터 셀지 모른다")
        void rejectsPartialBaseline() throws Exception {
            long subscription = subscription();

            putBaseline(subscription, "{\"count\": 41, \"amount\": 6050000}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-045"));
        }

        @Test
        @DisplayName("음수는 거부한다")
        void rejectsNegative() throws Exception {
            long subscription = subscription();

            baseline(subscription, -1, 6050000, "2026-08")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-045"));
        }

        @Test
        @DisplayName("미래 월은 거부한다 — 청약홈이 보여줄 수 없는 값이다")
        void rejectsFutureMonth() throws Exception {
            long subscription = subscription();

            baseline(subscription, 41, 6050000, "2026-10")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-045"));
        }

        @Test
        @DisplayName("청약이 아닌 자산에는 적을 수 없다")
        void rejectsNonSubscription() throws Exception {
            baseline(checking, 41, 6050000, "2026-08")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-040"));
        }
    }

    @Test
    @DisplayName("기준값만 적은 청약은 지워진다 — 기준값은 원장이 아니다")
    void deletesSubscriptionWithOnlyBaseline() throws Exception {
        long subscription = subscription();
        baseline(subscription, 38, 5300000, "2026-08");

        mockMvc.perform(delete("/api/ledger/assets/" + subscription)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }

    // --- 준비 ---

    private long subscription() throws Exception {
        String body = mockMvc.perform(post("/api/ledger/assets")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "주택청약", "type": "SAVINGS",
                                 "savingsKind": "HOUSING_SUBSCRIPTION"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private String deposit(long subscription, long amount, String occurredOn) throws Exception {
        return LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "TRANSFER", "amount": %d, "assetId": %d,
                 "counterAssetId": %d, "occurredOn": "%s"}
                """.formatted(amount, checking, subscription, occurredOn));
    }

    private ResultActions baseline(long assetId, int count, long amount, String throughMonth)
            throws Exception {
        return putBaseline(assetId, """
                {"count": %d, "amount": %d, "throughMonth": "%s"}
                """.formatted(count, amount, throughMonth));
    }

    private ResultActions putBaseline(long assetId, String json) throws Exception {
        return mockMvc.perform(put("/api/ledger/assets/%d/subscription/baseline".formatted(assetId))
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions getSubscription(long assetId) throws Exception {
        return mockMvc.perform(get("/api/ledger/assets/%d/subscription".formatted(assetId))
                .header(HttpHeaders.AUTHORIZATION, authHeader));
    }

    private ResultActions patchAsset(long assetId, String json) throws Exception {
        return mockMvc.perform(patch("/api/ledger/assets/" + assetId)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
