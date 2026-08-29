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

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 예산(#1264 · 확정 명세 §9).
 *
 * <p>게이지가 <b>2단</b>인 이유를 확인한다 — 확정분만 보여주면 「아직 절반 남았네」 하다가
 * 25일에 고정비가 빠지고 놀란다.
 *
 * <p>그리고 <b>구간을 저장한다</b>: 월 시작일을 나중에 바꿔도 지난달 예산의 구간은 그대로여야
 * 「지난달 같은 시점 대비」가 거짓말이 되지 않는다.
 *
 * <p>시계는 2026-01-15에 고정한다.
 */
@Import({StubExternalsConfig.class, FixedClockConfig.class})
class LedgerBudgetTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private long checking;
    private long food;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        checking = LedgerFixture.createAsset(mockMvc, authHeader, "급여통장", "CHECKING");
        food = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "식비");
    }

    @Nested
    @DisplayName("2단 게이지")
    class TwoStage {

        @Test
        @DisplayName("확정과 예정을 따로 담는다")
        void spentAndScheduled() throws Exception {
            putBudget("2026-01", 2000000, food, 500000);
            expense(120000, "2026-01-10");
            expense(30000, "2026-01-20");

            mockMvc.perform(get("/api/ledger/budget")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2026-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalAmount").value(2000000))
                    .andExpect(jsonPath("$.data.spent").value(120000))
                    .andExpect(jsonPath("$.data.scheduled").value(30000))
                    .andExpect(jsonPath("$.data.remaining").value(1850000))
                    // 2026-01-15부터 01-31까지 17일 — 이미 나갈 게 확정된 돈까지 뺀 뒤 나눈다.
                    .andExpect(jsonPath("$.data.daysLeft").value(17))
                    .andExpect(jsonPath("$.data.dailyAllowance").value(1850000 / 17));
        }

        /** 직접 예약만 세면 정기 항목이 통째로 빠진다 — 그게 「연한 부분」의 대부분이다. */
        @Test
        @DisplayName("정기 회차도 예정에 들어간다")
        void recurringCountsAsScheduled() throws Exception {
            putBudget("2026-01", 2000000, food, 500000);
            mockMvc.perform(post("/api/ledger/recurring")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "넷플릭스", "kind": "SUBSCRIPTION", "txType": "EXPENSE",
                                     "amount": 17000, "assetId": %d, "categoryId": %d,
                                     "freqType": "MONTHLY_DAY", "freqDay": 20,
                                     "startDate": "2026-01-01"}
                                    """.formatted(checking, food)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/ledger/budget")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2026-01"))
                    .andExpect(jsonPath("$.data.scheduled").value(17000))
                    .andExpect(jsonPath("$.data.categories[?(@.categoryId == %d)].scheduled"
                            .formatted(food)).value(contains(17000)));
        }

        /** 매달 나가기로 돼 있는 돈까지 쓸 수 있다고 착각하지 않게 미리 뺀다. */
        @Test
        @DisplayName("고정비를 미리 차감해 「쓸 수 있는 돈」을 남긴다")
        void fixedCostSubtracted() throws Exception {
            putBudget("2026-01", 2000000, food, 500000);
            mockMvc.perform(post("/api/ledger/recurring")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name": "월세", "kind": "FIXED_COST", "txType": "EXPENSE",
                             "amount": 800000, "assetId": %d, "freqType": "MONTHLY_DAY",
                             "freqDay": 5, "startDate": "2026-01-01"}
                            """.formatted(checking)));

            mockMvc.perform(get("/api/ledger/budget")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2026-01"))
                    .andExpect(jsonPath("$.data.fixedCostTotal").value(800000))
                    .andExpect(jsonPath("$.data.spendable").value(1200000));
        }

        @Test
        @DisplayName("예산을 세우지 않은 달도 쓴 돈은 보여준다")
        void noBudgetStillReports() throws Exception {
            expense(120000, "2026-01-10");

            mockMvc.perform(get("/api/ledger/budget")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalAmount").value(0))
                    .andExpect(jsonPath("$.data.spent").value(120000))
                    .andExpect(jsonPath("$.data.dailyAllowance").value(0));
        }
    }

    @Nested
    @DisplayName("구간은 저장된다")
    class StoredPeriod {

        /**
         * 나중에 월 시작일을 25일로 바꿔도 <b>1월 예산의 구간은 그대로</b>여야 한다.
         * 소급해서 달라지면 「지난달 같은 시점 대비」가 거짓말이 된다.
         */
        @Test
        @DisplayName("월 시작일을 바꿔도 이미 세운 예산의 구간은 안 흔들린다")
        void periodDoesNotShiftRetroactively() throws Exception {
            putBudget("2026-01", 2000000, food, 500000);

            mockMvc.perform(patch("/api/ledger/settings")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"monthStartDay": 25}
                                    """))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/ledger/budget")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2026-01"))
                    .andExpect(jsonPath("$.data.periodStart").value("2026-01-01"))
                    .andExpect(jsonPath("$.data.periodEnd").value("2026-01-31"));
        }

        @Test
        @DisplayName("새로 세우는 예산은 바뀐 월 시작일을 따른다")
        void newBudgetUsesNewSetting() throws Exception {
            mockMvc.perform(patch("/api/ledger/settings")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"monthStartDay": 25}
                            """));

            putBudget("2026-02", 2000000, food, 500000);

            mockMvc.perform(get("/api/ledger/budget")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2026-02"))
                    .andExpect(jsonPath("$.data.periodStart").value("2026-02-25"))
                    .andExpect(jsonPath("$.data.periodEnd").value("2026-03-24"));
        }
    }

    @Nested
    @DisplayName("카테고리 한도")
    class Categories {

        /** 화면에서 지운 한도가 남으면 사람이 고칠 수 없는 값이 된다. */
        @Test
        @DisplayName("PUT은 통째로 갈아 끼운다")
        void putReplacesAll() throws Exception {
            long cafe = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "카페/간식");
            putBudget("2026-01", 2000000, food, 500000);
            putBudget("2026-01", 2000000, cafe, 100000);

            mockMvc.perform(get("/api/ledger/budget")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2026-01"))
                    .andExpect(jsonPath("$.data.categories[?(@.categoryId == %d)]"
                            .formatted(food)).isEmpty())
                    .andExpect(jsonPath("$.data.categories[?(@.categoryId == %d)].amount"
                            .formatted(cafe)).value(contains(100000)));
        }

        /** 안 보이면 정리하지 않는다. */
        @Test
        @DisplayName("한도가 없어도 쓴 돈이 있으면 줄이 생긴다")
        void spentWithoutLimit() throws Exception {
            expense(120000, "2026-01-10");

            mockMvc.perform(get("/api/ledger/budget")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("period", "2026-01"))
                    .andExpect(jsonPath("$.data.categories[?(@.categoryId == %d)].spent"
                            .formatted(food)).value(contains(120000)));
        }
    }

    private void putBudget(String period, long total, long categoryId, long amount)
            throws Exception {
        mockMvc.perform(put("/api/ledger/budget")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("period", period)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"totalAmount": %d,
                                 "categories": [{"categoryId": %d, "amount": %d}]}
                                """.formatted(total, categoryId, amount)))
                .andExpect(status().isOk());
    }

    private void expense(long amount, String date) throws Exception {
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": %d, "assetId": %d, "categoryId": %d,
                 "occurredOn": "%s"}
                """.formatted(amount, checking, food, date));
    }
}
