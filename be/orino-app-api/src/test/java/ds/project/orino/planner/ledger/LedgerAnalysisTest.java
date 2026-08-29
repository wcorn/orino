package ds.project.orino.planner.ledger;

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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v2 분석(#1267) — 관점 전환 · 카드 실적 · 예상 잔액 곡선.
 *
 * <p>여기서 확인하는 것은 「숫자가 나오나」가 아니라 <b>두 관점이 갈리는 지점이 할부인가</b>다.
 * 할부가 없으면 두 값이 같아야 하고, 있으면 벌어져야 하며, 그 <b>이유까지</b> 서버가 말해야 한다.
 *
 * <p>시계를 못박는다(2026-01-15). 통계는 「지금이 몇 월인가」에 통째로 매달린 계산이다.
 */
@FixedClock
class LedgerAnalysisTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private long checking;
    private long card;
    private long food;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        checking = LedgerFixture.createAsset(mockMvc, authHeader, "급여통장", "CHECKING");
        card = LedgerFixture.createAsset(mockMvc, authHeader, "신한카드", "CREDIT_CARD");
        food = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "식비");
        mockMvc.perform(patch("/api/ledger/cards/" + card + "/cycle")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"cycleStartDay": 1, "cycleCloseDay": 99, "paymentDay": 14,
                         "paymentAssetId": %d}
                        """.formatted(checking)));
    }

    @Nested
    @DisplayName("관점 전환")
    class Perspectives {

        /** 할부가 없으면 두 관점이 같은 값이다 — v1에서 토글을 안 그린 이유가 그것이었다. */
        @Test
        @DisplayName("현금 지출만 있으면 두 관점이 같다")
        void sameWithoutCards() throws Exception {
            expense(checking, 120000, "2026-01-10");

            mockMvc.perform(get("/api/ledger/stats")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2026-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.perspective").value("SPEND"))
                    .andExpect(jsonPath("$.data.total").value(120000))
                    .andExpect(jsonPath("$.data.perspectiveDiff.otherTotal").value(120000))
                    .andExpect(jsonPath("$.data.perspectiveDiff.diff").value(0))
                    // 벌어지지 않으면 이유도 없다 — 이유 없는 안내를 그리지 않기 위해서다.
                    .andExpect(jsonPath("$.data.perspectiveDiff.reason").doesNotExist());
        }

        /**
         * <b>이 테스트가 §10.1의 전부다.</b> 1월에 30만원 3개월 할부로 사면 —
         * 소비 기준은 <b>1월에 30만</b>, 청구 기준은 회차가 청구되는 <b>2월부터</b>다.
         */
        @Test
        @DisplayName("할부에서 두 관점이 벌어지고, 그 이유를 말한다")
        void splitsOnInstallment() throws Exception {
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 300000, "assetId": %d, "categoryId": %d,
                     "occurredOn": "2026-01-09", "title": "노트북",
                     "installment": {"months": 3, "interestFree": true}}
                    """.formatted(card, food));

            // 소비 기준: 산 달에 전액.
            mockMvc.perform(get("/api/ledger/stats")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2026-01")
                            .param("perspective", "SPEND"))
                    .andExpect(jsonPath("$.data.total").value(300000))
                    .andExpect(jsonPath("$.data.perspectiveDiff.other").value("BILLING"))
                    .andExpect(jsonPath("$.data.perspectiveDiff.diff").value(-300000))
                    // 할부만 있으면 이유도 하나다.
                    .andExpect(jsonPath("$.data.perspectiveDiff.reason").value("할부 때문"));

            // 청구 기준: 1월에는 아직 청구된 게 없다. 회차는 2월부터다.
            mockMvc.perform(get("/api/ledger/stats")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2026-01")
                            .param("perspective", "BILLING"))
                    .andExpect(jsonPath("$.data.total").value(0))
                    .andExpect(jsonPath("$.data.perspectiveDiff.otherTotal").value(300000));
        }

        /**
         * 로컬에서 드러난 것이다 — 카드 사용 18만과 할부 30만이 함께 있는 달에
         * 「할부 때문」이라고만 적으니 <b>18만이 설명되지 않은 채 남았다.</b>
         * 차이가 48만인데 이유가 30만어치뿐이면 사람은 나머지를 자기가 찾아야 한다.
         */
        @Test
        @DisplayName("원인이 둘이면 둘 다 말한다")
        void namesBothCauses() throws Exception {
            expense(card, 180000, "2026-01-08");
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 300000, "assetId": %d, "categoryId": %d,
                     "occurredOn": "2026-01-09", "installment": {"months": 3}}
                    """.formatted(card, food));

            mockMvc.perform(get("/api/ledger/stats")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2026-01"))
                    .andExpect(jsonPath("$.data.total").value(480000))
                    .andExpect(jsonPath("$.data.perspectiveDiff.diff").value(-480000))
                    .andExpect(jsonPath("$.data.perspectiveDiff.reason")
                            .value("할부와 카드 사이클 경계 때문"));
        }

        /** 카드 사용만 있으면 사이클 경계가 유일한 이유다. */
        @Test
        @DisplayName("할부 없이 카드만 쓰면 사이클 경계가 이유다")
        void cycleBoundaryOnly() throws Exception {
            expense(card, 180000, "2026-01-08");

            mockMvc.perform(get("/api/ledger/stats")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2026-01"))
                    .andExpect(jsonPath("$.data.perspectiveDiff.reason")
                            .value("카드 사이클 경계 때문"));
        }

        /** 설정의 기본 관점을 따른다. 파라미터는 그때그때 덮어쓰는 것뿐이다. */
        @Test
        @DisplayName("관점을 안 주면 설정의 기본값을 쓴다")
        void fallsBackToSettings() throws Exception {
            mockMvc.perform(patch("/api/ledger/settings")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"defaultPerspective": "BILLING"}
                            """));

            mockMvc.perform(get("/api/ledger/stats")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2026-01"))
                    .andExpect(jsonPath("$.data.perspective").value("BILLING"));
        }

        /**
         * <b>청구서·예정 API는 관점을 받지 않는다</b>(§10.1). 파라미터를 붙여 보내도
         * 응답이 달라지지 않아야 한다 — 「9월 14일에 얼마 빠지나」에 소비 관점이 낄 자리가 없다.
         */
        @Test
        @DisplayName("예정 API는 perspective를 무시한다")
        void upcomingIgnoresPerspective() throws Exception {
            expense(checking, 120000, "2026-01-10");

            String plain = mockMvc.perform(get("/api/ledger/upcoming")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andReturn().getResponse().getContentAsString();
            String withParam = mockMvc.perform(get("/api/ledger/upcoming")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("perspective", "SPEND"))
                    .andReturn().getResponse().getContentAsString();

            org.assertj.core.api.Assertions.assertThat(withParam).isEqualTo(plain);
        }
    }

    @Nested
    @DisplayName("고정 대 변동")
    class FixedVsVariable {

        /** 속성을 안 정한 카테고리를 변동비에 몰아넣으면 「변동비 100%」라는 거짓말이 나온다. */
        @Test
        @DisplayName("속성을 안 정한 지출은 따로 센다")
        void unclassifiedStaysApart() throws Exception {
            expense(checking, 120000, "2026-01-10");

            mockMvc.perform(get("/api/ledger/stats")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2026-01"))
                    .andExpect(jsonPath("$.data.fixedVsVariable.fixed").value(0))
                    .andExpect(jsonPath("$.data.fixedVsVariable.variable").value(0))
                    .andExpect(jsonPath("$.data.fixedVsVariable.unclassified").value(120000));
        }

        @Test
        @DisplayName("카테고리에 고정비를 붙이면 그쪽으로 센다")
        void countsFixed() throws Exception {
            mockMvc.perform(patch("/api/ledger/categories/" + food)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"costType": "FIXED"}
                                    """))
                    .andExpect(status().isOk());
            expense(checking, 120000, "2026-01-10");

            mockMvc.perform(get("/api/ledger/stats")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2026-01"))
                    .andExpect(jsonPath("$.data.fixedVsVariable.fixed").value(120000))
                    .andExpect(jsonPath("$.data.fixedVsVariable.unclassified").value(0));
        }

        /**
         * 로컬에서 드러났다 — 추이 막대가 그 달 지출보다 짧았다.
         *
         * <p>월별 점이 고정·변동만 담고 있어서 속성을 안 정한 지출이 <b>막대에서 통째로
         * 사라졌다.</b> 셋을 더해야 그 달의 지출이 된다.
         */
        @Test
        @DisplayName("월별 추이도 안 정한 지출을 잃지 않는다")
        void monthlyKeepsUnclassified() throws Exception {
            mockMvc.perform(patch("/api/ledger/categories/" + food)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"costType": "FIXED"}
                                    """))
                    .andExpect(status().isOk());
            expense(checking, 120000, "2026-01-10");
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 80000, "assetId": %d,
                     "occurredOn": "2026-01-11", "title": "분류 안 한 지출"}
                    """.formatted(checking));

            mockMvc.perform(get("/api/ledger/stats")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2026-01"))
                    .andExpect(jsonPath("$.data.monthly[11].expense").value(200000))
                    .andExpect(jsonPath("$.data.monthly[11].fixed").value(120000))
                    .andExpect(jsonPath("$.data.monthly[11].variable").value(0))
                    .andExpect(jsonPath("$.data.monthly[11].unclassified").value(80000));
        }
    }

    /**
     * 자산별 지출.
     *
     * <p>로컬에서 드러났다 — 청구 기준으로 보는데 자산 목록만 소비 기준이라, 합계에 없는
     * 카드 사용이 <b>88%짜리 줄</b>로 남았다. 비율의 분모가 제 것이 아니면 막대는 칸을 넘고
     * 줄을 다 더해도 합계가 안 된다.
     */
    @Nested
    @DisplayName("자산별 지출")
    class ByAsset {

        @Test
        @DisplayName("관점을 따라간다 — 청구 기준이면 아직 청구 안 된 카드는 자리도 없다")
        void followsPerspective() throws Exception {
            expense(checking, 42000, "2026-01-12");
            expense(card, 180000, "2026-01-08");

            // 소비 기준: 둘 다 있고, 두 줄을 더하면 합계가 된다.
            mockMvc.perform(get("/api/ledger/stats")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2026-01")
                            .param("perspective", "SPEND"))
                    .andExpect(jsonPath("$.data.total").value(222000))
                    .andExpect(jsonPath("$.data.byAsset.length()").value(2));

            // 청구 기준: 1월에 결제일이 오는 청구서가 없으니 카드는 셀 것이 없다.
            mockMvc.perform(get("/api/ledger/stats")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2026-01")
                            .param("perspective", "BILLING"))
                    .andExpect(jsonPath("$.data.total").value(42000))
                    .andExpect(jsonPath("$.data.byAsset.length()").value(1))
                    .andExpect(jsonPath("$.data.byAsset[0].amount").value(42000))
                    .andExpect(jsonPath("$.data.byAsset[0].share").value(1.0));
        }
    }

    @Nested
    @DisplayName("카드 실적")
    class UsageGoal {

        /** 조건을 안 걸어 둔 카드는 0%가 아니라 <b>없음</b>이다. */
        @Test
        @DisplayName("실적을 안 걸면 진행률 자체가 없다")
        void absentWithoutGoal() throws Exception {
            mockMvc.perform(get("/api/ledger/cards")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.cards[0].usageGoal").doesNotExist());
        }

        @Test
        @DisplayName("승인 기준은 긁은 날 전액을 센다 — 할부도 산 달에 전액이다")
        void approvalCountsFullAmount() throws Exception {
            setGoal(500000, "APPROVAL");
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 300000, "assetId": %d, "categoryId": %d,
                     "occurredOn": "2026-01-09", "installment": {"months": 3}}
                    """.formatted(card, food));

            mockMvc.perform(get("/api/ledger/cards")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.cards[0].usageGoal.basis").value("APPROVAL"))
                    .andExpect(jsonPath("$.data.cards[0].usageGoal.counted").value(300000))
                    .andExpect(jsonPath("$.data.cards[0].usageGoal.remaining").value(200000))
                    .andExpect(jsonPath("$.data.cards[0].usageGoal.achieved").value(false));
        }

        /** 제외는 카테고리가 정한다 — 세금·보험료처럼 카드사가 안 세는 것들이다. */
        @Test
        @DisplayName("실적 제외 카테고리는 빠진다")
        void excludedCategoryDoesNotCount() throws Exception {
            setGoal(500000, "APPROVAL");
            mockMvc.perform(patch("/api/ledger/categories/" + food)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"excludeFromCardGoal": true}
                            """));
            expense(card, 180000, "2026-01-10");

            mockMvc.perform(get("/api/ledger/cards")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.cards[0].usageGoal.counted").value(0));
        }

        @Test
        @DisplayName("신용카드가 아닌 자산에는 실적을 걸 수 없다")
        void rejectsNonCard() throws Exception {
            mockMvc.perform(patch("/api/ledger/cards/" + checking + "/usage-goal")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"goalAmount": 500000, "basis": "APPROVAL"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-010"));
        }
    }

    @Nested
    @DisplayName("예상 잔액 곡선")
    class BalanceCurve {

        /** 아무 일도 없는 날에도 점을 찍는다 — 빼면 화면이 날짜 간격을 스스로 메운다. */
        @Test
        @DisplayName("하루도 빠짐없이 점을 찍는다")
        void everyDayHasPoint() throws Exception {
            mockMvc.perform(get("/api/ledger/upcoming/balance-curve")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("days", "7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.points", hasSize(8)))
                    .andExpect(jsonPath("$.data.points[0].date").value("2026-01-15"));
        }

        /** 마이너스가 되는 날을 미리 알린다. 없으면 {@code null}이다 — 0으로 두면 오늘이 된다. */
        @Test
        @DisplayName("잔액이 마이너스가 되는 첫날을 알린다")
        void warnsFirstNegativeDay() throws Exception {
            income(checking, 100000, "2026-01-10");
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 500000, "assetId": %d, "categoryId": %d,
                     "occurredOn": "2026-01-20", "title": "재산세"}
                    """.formatted(checking, food));

            mockMvc.perform(get("/api/ledger/upcoming/balance-curve")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("days", "30"))
                    .andExpect(jsonPath("$.data.currentBalance").value(100000))
                    .andExpect(jsonPath("$.data.firstNegativeDate").value("2026-01-20"));
        }

        @Test
        @DisplayName("마이너스가 안 되면 그 날짜는 없다")
        void noNegativeDay() throws Exception {
            income(checking, 3000000, "2026-01-10");

            mockMvc.perform(get("/api/ledger/upcoming/balance-curve")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.firstNegativeDate").doesNotExist());
        }
    }

    @Nested
    @DisplayName("복합 검색")
    class Search {

        @Test
        @DisplayName("금액 범위와 내용을 함께 건다")
        void combinesConditions() throws Exception {
            expenseTitled(checking, 4500, "2026-01-05", "스타벅스 역삼");
            expenseTitled(checking, 120000, "2026-01-06", "스타벅스 굿즈");
            expenseTitled(checking, 9000, "2026-01-07", "김밥천국");

            mockMvc.perform(post("/api/ledger/stats/search")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"from": "2026-01-01", "to": "2026-01-31",
                                     "keyword": "스타벅스", "maxAmount": 10000}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.count").value(1))
                    .andExpect(jsonPath("$.data.total").value(4500))
                    .andExpect(jsonPath("$.data.items[0].title").value("스타벅스 역삼"))
                    .andExpect(jsonPath("$.data.truncated").value(false));
        }
    }

    // ── 준비물 ────────────────────────────────────────────────────────────────

    private void setGoal(long amount, String basis) throws Exception {
        mockMvc.perform(patch("/api/ledger/cards/" + card + "/usage-goal")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goalAmount": %d, "basis": "%s"}
                                """.formatted(amount, basis)))
                .andExpect(status().isOk());
    }

    private void expense(long assetId, long amount, String date) throws Exception {
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": %d, "assetId": %d, "categoryId": %d,
                 "occurredOn": "%s"}
                """.formatted(amount, assetId, food, date));
    }

    private void expenseTitled(long assetId, long amount, String date, String title)
            throws Exception {
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": %d, "assetId": %d, "categoryId": %d,
                 "occurredOn": "%s", "title": "%s"}
                """.formatted(amount, assetId, food, date, title));
    }

    private void income(long assetId, long amount, String date) throws Exception {
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "INCOME", "amount": %d, "assetId": %d, "occurredOn": "%s"}
                """.formatted(amount, assetId, date));
    }
}
