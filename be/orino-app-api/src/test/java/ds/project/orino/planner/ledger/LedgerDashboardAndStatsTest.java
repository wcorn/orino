package ds.project.orino.planner.ledger;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.FixedClockConfig;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.StubExternalsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Clock;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 대시보드 · 통계 · 잔액 맞추기(#1261).
 *
 * <p>기간을 단언하는 테스트라 <b>시계를 못박는다</b>(2026-01-15). 실시각을 쓰면 월말에만 깨지고,
 * 그때 원인을 찾는 데 드는 시간이 이 설정을 두는 값보다 훨씬 비싸다.
 */
@Import({StubExternalsConfig.class, FixedClockConfig.class})
class LedgerDashboardAndStatsTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private Clock clock;

    private String authHeader;
    private long checking;
    private long savings;
    private long food;
    private long cafe;
    private long salary;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);

        checking = LedgerFixture.createAsset(mockMvc, authHeader, "급여통장", "CHECKING");
        savings = LedgerFixture.createAsset(mockMvc, authHeader, "비상금", "SAVINGS");
        food = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "식비");
        cafe = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "카페/간식");
        salary = LedgerFixture.categoryIdByName(mockMvc, authHeader, "INCOME", "급여");
    }

    @Nested
    @DisplayName("대시보드 — v1은 껍데기다")
    class Dashboard {

        /**
         * v1에서는 이 블록들을 <b>아예 내리지 않았다</b>(D-7) — 예정이 없으면 그릴 수 없어서
         * 빈 카드를 만드느니 필드를 없앴다. v1.5(#1264)에서 예정이 생겨 자리가 채워졌다.
         */
        @Test
        @DisplayName("v1.5에서 2축 요약·다가오는 결제·순자산 자리가 생긴다")
        void hasV15Blocks() throws Exception {
            mockMvc.perform(get("/api/ledger/dashboard")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.cashflow").exists())
                    .andExpect(jsonPath("$.data.upcoming").exists())
                    .andExpect(jsonPath("$.data.netWorth").exists())
                    .andExpect(jsonPath("$.data.todo.overdue").value(0));
        }

        @Test
        @DisplayName("이미 쓴 돈 · 이번 달 수입 · 정리할 내역 셋을 준다")
        void givesThreeNumbers() throws Exception {
            expense(checking, 120000, food);
            income(checking, 3850000);
            uncategorized(checking, 4500);

            mockMvc.perform(get("/api/ledger/dashboard")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.spending.spent").value(124500))
                    .andExpect(jsonPath("$.data.income.amount").value(3850000))
                    .andExpect(jsonPath("$.data.todo.uncategorized").value(1));
        }

        @Test
        @DisplayName("이체는 쓴 돈에 들어가지 않는다")
        void transferIsNotSpending() throws Exception {
            expense(checking, 120000, food);
            transfer(checking, savings, 500000);

            mockMvc.perform(get("/api/ledger/dashboard")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.spending.spent").value(120000));
        }
    }

    @Nested
    @DisplayName("카테고리 통계")
    class Stats {

        @Test
        @DisplayName("많이 쓴 순으로 주고 비율까지 계산해 준다")
        void ranksCategories() throws Exception {
            expense(checking, 300000, food);
            expense(checking, 100000, cafe);

            mockMvc.perform(get("/api/ledger/stats")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(400000))
                    .andExpect(jsonPath("$.data.byCategory", hasSize(2)))
                    .andExpect(jsonPath("$.data.byCategory[0].categoryName").value("식비"))
                    .andExpect(jsonPath("$.data.byCategory[0].share").value(0.75));
        }

        @Test
        @DisplayName("미분류도 한 칸을 차지한다 — 빼면 정리하지 않는다")
        void keepsUncategorized() throws Exception {
            expense(checking, 300000, food);
            uncategorized(checking, 100000);

            mockMvc.perform(get("/api/ledger/stats")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.byCategory", hasSize(2)))
                    .andExpect(jsonPath("$.data.byCategory[1].categoryId").doesNotExist())
                    .andExpect(jsonPath("$.data.byCategory[1].amount").value(100000));
        }

        @Test
        @DisplayName("이체는 통계에 들어가지 않는다")
        void excludesTransfer() throws Exception {
            expense(checking, 300000, food);
            transfer(checking, savings, 500000);

            mockMvc.perform(get("/api/ledger/stats")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.total").value(300000))
                    .andExpect(jsonPath("$.data.byCategory", hasSize(1)));
        }

        @Test
        @DisplayName("환불은 그 카테고리의 지출을 깎는다 — 수입으로 새지 않는다")
        void refundReducesCategory() throws Exception {
            long id = LedgerFixture.transactionId(LedgerFixture.createTransaction(
                    mockMvc, authHeader, """
                            {"type": "EXPENSE", "amount": 300000, "assetId": %d,
                             "categoryId": %d, "occurredOn": "%s"}
                            """.formatted(checking, food, today())));
            mockMvc.perform(post("/api/ledger/transactions/%d/refund".formatted(id))
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": 100000}"));

            mockMvc.perform(get("/api/ledger/stats")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.total").value(200000))
                    .andExpect(jsonPath("$.data.byCategory[0].amount").value(200000));
        }

        @Test
        @DisplayName("지난 구간과 견줘 준다 — 숫자 하나로는 많이 썼는지 알 수 없다")
        void comparesWithPreviousPeriod() throws Exception {
            // 지난달 20만, 이번달 30만.
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 200000, "assetId": %d,
                     "categoryId": %d, "occurredOn": "%s"}
                    """.formatted(checking, food, today().minusMonths(1)));
            expense(checking, 300000, food);

            mockMvc.perform(get("/api/ledger/stats")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.comparison.previousPeriod.total").value(200000))
                    .andExpect(jsonPath("$.data.comparison.previousPeriod.diff").value(100000))
                    .andExpect(jsonPath("$.data.comparison.previousYear.total").value(0));
        }

        @Test
        @DisplayName("지난 달을 직접 지정해 볼 수 있다")
        void acceptsExplicitPeriod() throws Exception {
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 200000, "assetId": %d,
                     "categoryId": %d, "occurredOn": "%s"}
                    """.formatted(checking, food, today().minusMonths(1)));

            mockMvc.perform(get("/api/ledger/stats")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2025-12"))
                    .andExpect(jsonPath("$.data.total").value(200000))
                    .andExpect(jsonPath("$.data.period.label").value("2025-12"));
        }
    }

    @Nested
    @DisplayName("잔액 맞추기")
    class Reconcile {

        @Test
        @DisplayName("차액을 조정 거래로 만들고 나면 잔액이 실제와 같아진다")
        void adjustsToActualBalance() throws Exception {
            income(checking, 1000000);
            expense(checking, 30000, food);

            // 원장은 970,000인데 통장에는 950,000이 있다 — 어딘가 20,000을 안 적었다.
            mockMvc.perform(post("/api/ledger/assets/%d/reconcile".formatted(checking))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"actualBalance\": 950000}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.difference").value(-20000))
                    .andExpect(jsonPath("$.data.adjustmentTransactionId").isNumber());

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.groups[0].assets[?(@.name=='급여통장')].balance")
                            .value(950000));
        }

        @Test
        @DisplayName("차이가 0이면 거래를 만들지 않는다 — 없는 거래를 적지 않는다")
        void doesNothingWhenAlreadyMatching() throws Exception {
            income(checking, 1000000);

            mockMvc.perform(post("/api/ledger/assets/%d/reconcile".formatted(checking))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"actualBalance\": 1000000}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.difference").value(0))
                    .andExpect(jsonPath("$.data.adjustmentTransactionId").doesNotExist());

            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    // 수입 한 건뿐이다. 0원짜리 조정 줄이 끼어들지 않았다.
                    .andExpect(jsonPath("$.data.groups[0].items", hasSize(1)));
        }

        @Test
        @DisplayName("조정 거래는 미분류다 — 돈이 어디로 갔는지 모르는 게 사실이다")
        void adjustmentIsUncategorized() throws Exception {
            income(checking, 1000000);
            mockMvc.perform(post("/api/ledger/assets/%d/reconcile".formatted(checking))
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"actualBalance\": 980000}"));

            mockMvc.perform(get("/api/ledger/dashboard")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.todo.uncategorized").value(1))
                    .andExpect(jsonPath("$.data.spending.spent").value(20000));
        }

        @Test
        @DisplayName("잔액을 갖지 않는 자산에서는 맞출 것이 없다")
        void rejectsCardReconcile() throws Exception {
            long card = LedgerFixture.createAsset(mockMvc, authHeader, "신한", "CREDIT_CARD");

            mockMvc.perform(post("/api/ledger/assets/%d/reconcile".formatted(card))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"actualBalance\": 0}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // --- 준비 ---

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), TEST_ZONE);
    }

    private void expense(long assetId, long amount, long categoryId) throws Exception {
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": %d, "assetId": %d,
                 "categoryId": %d, "occurredOn": "%s"}
                """.formatted(amount, assetId, categoryId, today()));
    }

    private void uncategorized(long assetId, long amount) throws Exception {
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": %d, "assetId": %d, "occurredOn": "%s"}
                """.formatted(amount, assetId, today()));
    }

    private void income(long assetId, long amount) throws Exception {
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "INCOME", "amount": %d, "assetId": %d,
                 "categoryId": %d, "occurredOn": "%s"}
                """.formatted(amount, assetId, salary, today()));
    }

    private void transfer(long from, long to, long amount) throws Exception {
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "TRANSFER", "amount": %d, "assetId": %d,
                 "counterAssetId": %d, "occurredOn": "%s"}
                """.formatted(amount, from, to, today()));
    }
}
