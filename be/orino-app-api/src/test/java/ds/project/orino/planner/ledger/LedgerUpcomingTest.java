package ds.project.orino.planner.ledger;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.planner.ledger.upcoming.ScheduledPromotionScheduler;
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

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 예정 4출처 UNION과 2축 요약(#1264).
 *
 * <p>여기서 확인하는 것은 <b>같은 돈이 두 번 세어지지 않는가</b>다. 카드로 쓴 돈은 소비에
 * 한 번, 대금에 또 한 번 세어지기 쉽고, 그러면 「이번 달 얼마 쓰나」가 두 배가 된다.
 *
 * <p>그리고 <b>예정은 원장 잔액을 바꾸지 않는다.</b> 이 불변 조건이 깨지면 「월말 예상 잔액」이
 * 현재 잔액과 같아져 아무 말도 하지 않게 된다.
 *
 * <p>시계를 못박는다(2026-01-15, 목요일). 예정은 오늘로부터 세는 값이라 실시각으로는
 * 매일 다른 결과가 난다.
 */
@FixedClock
class LedgerUpcomingTest extends ApiTestSupport {

    private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private ScheduledPromotionScheduler promotionScheduler;
    @Autowired
    private ds.project.orino.planner.ledger.card.StatementCycleScheduler cycleScheduler;

    private String authHeader;
    private long checking;
    private long savings;
    private long card;
    private long food;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        checking = LedgerFixture.createAsset(mockMvc, authHeader, "급여통장", "CHECKING");
        savings = LedgerFixture.createAsset(mockMvc, authHeader, "청약", "SAVINGS");
        card = LedgerFixture.createAsset(mockMvc, authHeader, "신한카드", "CREDIT_CARD");
        food = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "식비");
        mockMvc.perform(patch("/api/ledger/cards/" + card + "/cycle")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"cycleStartDay": 1, "cycleCloseDay": 99, "paymentDay": 14,
                         "paymentAssetId": %d}
                        """.formatted(checking)));
        transaction("""
                {"type": "INCOME", "amount": 3000000, "assetId": %d, "occurredOn": "2026-01-05"}
                """.formatted(checking));
    }

    @Nested
    @DisplayName("네 출처가 한 목록으로 모인다")
    class FourSources {

        /**
         * 60일을 본다 — 1회차는 산 달 청구서에 붙어 카드 대금에 들어가므로(#1279),
         * 「할부 잔여」로 따로 서는 것은 <b>그다음 달 회차</b>부터다.
         */
        @Test
        @DisplayName("정기 회차 · 직접 예약 · 카드 대금 · 할부 잔여가 모두 나온다")
        void allFour() throws Exception {
            recurring("넷플릭스", 17000, 20, checking);
            oneOff("재산세", 500000, "2026-01-20");
            cardExpense(180000, "2026-01-08");
            installment(300000, "2026-01-09");

            mockMvc.perform(get("/api/ledger/upcoming")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("days", "60"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.stats.byKind.RECURRING").value(2))
                    .andExpect(jsonPath("$.data.stats.byKind.ONE_OFF").value(1))
                    .andExpect(jsonPath("$.data.stats.byKind.CARD_PAYMENT").value(1))
                    .andExpect(jsonPath("$.data.stats.byKind.INSTALLMENT").value(1))
                    .andExpect(jsonPath("$.data.items", hasSize(5)));
        }

        /**
         * 붙은 회차를 함께 세면 그 청구서의 청구액과 같은 돈이 두 번 잡힌다.
         *
         * <p>1회차는 산 달 청구서에 붙으므로 카드 대금 <b>안에</b> 있고, 예정 목록에는
         * 「할부 잔여」로 따로 서지 않는다 — 30일 안에서는 카드 대금 한 줄이 전부다.
         */
        @Test
        @DisplayName("청구서에 붙은 할부 회차는 예정에 따로 나오지 않는다")
        void attachedRoundIsNotCountedTwice() throws Exception {
            installment(300000, "2026-01-09");

            mockMvc.perform(get("/api/ledger/upcoming")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("days", "30"))
                    .andExpect(jsonPath("$.data.stats.byKind.INSTALLMENT").doesNotExist())
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].kind").value("CARD_PAYMENT"))
                    // 300,000 ÷ 3 = 100,000. 카드 대금이 그 1회차를 담는다.
                    .andExpect(jsonPath("$.data.items[0].amount").value(100000));
        }

        @Test
        @DisplayName("날짜순으로 정렬되고 D-day가 붙는다")
        void sortedWithDday() throws Exception {
            oneOff("재산세", 500000, "2026-01-20");
            recurring("넷플릭스", 17000, 18, checking);

            mockMvc.perform(get("/api/ledger/upcoming")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.items[0].date").value("2026-01-18"))
                    .andExpect(jsonPath("$.data.items[0].dday").value(3))
                    .andExpect(jsonPath("$.data.items[1].date").value("2026-01-20"))
                    .andExpect(jsonPath("$.data.items[1].dday").value(5));
        }

        /** 미납은 지났어도 목록에 남는다 — 사라지면 「무시」 버튼을 만든 것과 같다(§6.4). */
        @Test
        @DisplayName("미납 회차는 날짜가 지나도 목록에 남는다")
        void unpaidStays() throws Exception {
            long id = recurring("보험료", 42000, 10, checking);
            mockMvc.perform(patch("/api/ledger/upcoming/occurrence")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"recurringId": %d, "occurrenceDate": "2026-01-10",
                                     "action": "UNPAID"}
                                    """.formatted(id)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/ledger/upcoming")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.items[0].overdue").value(true))
                    .andExpect(jsonPath("$.data.items[0].dday").value(-5))
                    .andExpect(jsonPath("$.data.items[0].amount").value(42000));
        }
    }

    @Nested
    @DisplayName("2축 요약 — 섞으면 안 된다")
    class TwoAxes {

        /**
         * <b>이 테스트가 §8.2의 전부다.</b> 두 축은 같은 달에 <b>다른 숫자</b>를 답한다 —
         * 이번 달 소비는 이번 달 카드 사용(12만)이고, 이번 달 출금은 <b>지난달</b> 카드값(18만)이다.
         *
         * <p>한 숫자로 합치면 30만이 되는데, 그건 어느 질문에도 답하지 않는 숫자다.
         */
        @Test
        @DisplayName("카드 대금은 cashflow에만, 카드 사용은 spending에만 잡힌다")
        void twoAxesAnswerDifferently() throws Exception {
            cardExpense(180000, "2025-12-20");
            cycleScheduler.rollCycles();
            cardExpense(120000, "2026-01-08");

            mockMvc.perform(get("/api/ledger/dashboard")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    // 소비 축(소비 시점): 이번 달 카드 사용 12만. 대금은 여기 없다.
                    .andExpect(jsonPath("$.data.spending.spent").value(120000))
                    .andExpect(jsonPath("$.data.spending.scheduled").value(0))
                    // 현금 축(출금 시점): 1월 14일에 빠질 지난달 카드값 18만. 이건 이체다.
                    .andExpect(jsonPath("$.data.cashflow.remainingOutflow").value(180000))
                    .andExpect(jsonPath("$.data.cashflow.balance").value(3000000))
                    .andExpect(jsonPath("$.data.cashflow.monthEndBalance").value(2820000));
        }

        /** 청약으로 옮긴 돈은 총자산에 남지만 <b>이번 달 쓸 수 있는 돈</b>에서는 빠진다. */
        @Test
        @DisplayName("저축 이체는 소비가 아니지만 쓸 수 있는 돈은 줄인다")
        void savingsTransferReducesSpendable() throws Exception {
            oneOffTransfer("청약 이체", 200000, "2026-01-25");

            mockMvc.perform(get("/api/ledger/dashboard")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.spending.estimate").value(0))
                    .andExpect(jsonPath("$.data.cashflow.remainingOutflow").value(200000))
                    .andExpect(jsonPath("$.data.cashflow.monthEndBalance").value(2800000));
        }

        /** 월말 숫자만 보면 괜찮아 보이는데 중간에 바닥나는 달이 있다. */
        @Test
        @DisplayName("가장 낮아지는 지점과 그 이유를 준다")
        void minBalance() throws Exception {
            oneOff("재산세", 2500000, "2026-01-20");
            transaction("""
                    {"type": "INCOME", "amount": 2000000, "assetId": %d,
                     "occurredOn": "2026-01-28"}
                    """.formatted(checking));

            mockMvc.perform(get("/api/ledger/upcoming")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.stats.minBalance.amount").value(500000))
                    .andExpect(jsonPath("$.data.stats.minBalance.date").value("2026-01-20"))
                    .andExpect(jsonPath("$.data.stats.minBalance.reason").value("재산세"))
                    .andExpect(jsonPath("$.data.stats.expectedBalance").value(2500000));
        }

        /**
         * 예정이 잔액을 바꾸면 「월말 예상 잔액」이 현재 잔액과 같아져 아무 말도 하지 않게 된다.
         */
        @Test
        @DisplayName("예정은 원장 잔액을 바꾸지 않는다")
        void scheduledDoesNotTouchLedger() throws Exception {
            oneOff("재산세", 500000, "2026-01-20");
            recurring("넷플릭스", 17000, 20, checking);

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.totalAssets").value(3000000));
            mockMvc.perform(get("/api/ledger/summary")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.monthEndBalance").value(2483000));
        }

        @Test
        @DisplayName("미납은 대시보드의 정리할 것에 잡힌다")
        void overdueCounted() throws Exception {
            long id = recurring("보험료", 42000, 10, checking);
            mockMvc.perform(patch("/api/ledger/upcoming/occurrence")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"recurringId": %d, "occurrenceDate": "2026-01-10", "action": "UNPAID"}
                            """.formatted(id)));

            mockMvc.perform(get("/api/ledger/dashboard")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.todo.overdue").value(1));
        }
    }

    @Nested
    @DisplayName("직접 예약")
    class OneOff {

        @Test
        @DisplayName("예정일이 지나면 확정으로 올라간다")
        void promotedWhenDue() throws Exception {
            oneOff("재산세", 500000, "2026-01-20");

            promotionScheduler.promoteDueOn(LocalDate.of(2026, 1, 20));

            mockMvc.perform(get("/api/ledger/upcoming")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.items", hasSize(0)));
            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("from", "2026-01-01").param("to", "2026-01-31"))
                    .andExpect(jsonPath("$.data.monthTotals.expense").value(500000))
                    .andExpect(jsonPath("$.data.monthTotals.scheduledExpense").value(0));
        }

        @Test
        @DisplayName("아직 안 온 예약은 그대로 예정이다")
        void notYetDue() throws Exception {
            oneOff("재산세", 500000, "2026-01-20");

            promotionScheduler.promoteDueOn(LocalDate.of(2026, 1, 19));

            mockMvc.perform(get("/api/ledger/upcoming")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.items", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("캘린더")
    class Calendar {

        /** 두 화면이 서로 다른 말을 하지 않으려면 예정도 4출처에서 와야 한다. */
        @Test
        @DisplayName("과거는 확정, 미래는 예정을 따로 담는다")
        void splitsConfirmedAndScheduled() throws Exception {
            transaction("""
                    {"type": "EXPENSE", "amount": 120000, "assetId": %d, "categoryId": %d,
                     "occurredOn": "2026-01-10"}
                    """.formatted(checking, food));
            recurring("넷플릭스", 17000, 20, checking);

            mockMvc.perform(get("/api/ledger/transactions/calendar")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("month", "2026-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.todayLine").value("2026-01-15"))
                    .andExpect(jsonPath("$.data.days[?(@.date == '2026-01-10')].expense")
                            .value(org.hamcrest.Matchers.contains(120000)))
                    .andExpect(jsonPath("$.data.days[?(@.date == '2026-01-20')].scheduledExpense")
                            .value(org.hamcrest.Matchers.contains(17000)))
                    .andExpect(jsonPath("$.data.days[?(@.date == '2026-01-20')].expense")
                            .value(org.hamcrest.Matchers.contains(0)));
        }

        /**
         * 로컬에서 실제로 나온 버그다. 캘린더는 <b>달의 첫날부터</b> 물어보는데, 그때 규칙을
         * 그대로 전개하면 이미 지난 회차가 「예정」으로 그려졌다. 그날은 아무 일도 없었다 —
         * 적혔으면 원장에 있고, 안 적혔으면 건너뛰었거나 되돌렸거나 미납이다.
         */
        @Test
        @DisplayName("지난 회차를 예정으로 그리지 않는다")
        void noScheduledInThePast() throws Exception {
            recurring("보험료", 42000, 10, checking);

            mockMvc.perform(get("/api/ledger/transactions/calendar")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("month", "2026-01"))
                    .andExpect(jsonPath("$.data.days[?(@.date == '2026-01-10')]").isEmpty());
        }
    }

    @Nested
    @DisplayName("월 시작일")
    class MonthStart {

        /** 25일 시작이면 1월 15일은 아직 <b>12월 25일에 시작한 구간</b>이다. */
        @Test
        @DisplayName("25일이면 구간 경계가 달을 넘어간다")
        void paydayPeriod() throws Exception {
            mockMvc.perform(patch("/api/ledger/settings")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"monthStartDay": 25}
                                    """))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/ledger/dashboard")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.period.start").value("2025-12-25"))
                    .andExpect(jsonPath("$.data.period.end").value("2026-01-24"))
                    .andExpect(jsonPath("$.data.period.monthStartDay").value(25));
        }
    }

    // ── 준비물 ────────────────────────────────────────────────────────────────

    private void transaction(String json) throws Exception {
        LedgerFixture.createTransaction(mockMvc, authHeader, json);
    }

    private long recurring(String name, long amount, int day, long assetId) throws Exception {
        String body = mockMvc.perform(post("/api/ledger/recurring")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "kind": "SUBSCRIPTION", "txType": "EXPENSE",
                                 "amount": %d, "assetId": %d, "freqType": "MONTHLY_DAY",
                                 "freqDay": %d, "startDate": "2026-01-01"}
                                """.formatted(name, amount, assetId, day)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.id")).longValue();
    }

    private void oneOff(String title, long amount, String date) throws Exception {
        transaction("""
                {"type": "EXPENSE", "amount": %d, "assetId": %d, "title": "%s",
                 "occurredOn": "%s"}
                """.formatted(amount, checking, title, date));
    }

    private void oneOffTransfer(String title, long amount, String date) throws Exception {
        transaction("""
                {"type": "TRANSFER", "amount": %d, "assetId": %d, "counterAssetId": %d,
                 "title": "%s", "occurredOn": "%s"}
                """.formatted(amount, checking, savings, title, date));
    }

    private void cardExpense(long amount, String date) throws Exception {
        transaction("""
                {"type": "EXPENSE", "amount": %d, "assetId": %d, "categoryId": %d,
                 "title": "카드 지출", "occurredOn": "%s"}
                """.formatted(amount, card, food, date));
    }

    private void installment(long amount, String date) throws Exception {
        transaction("""
                {"type": "EXPENSE", "amount": %d, "assetId": %d, "categoryId": %d,
                 "title": "노트북", "occurredOn": "%s",
                 "installment": {"months": 3, "interestFree": true}}
                """.formatted(amount, card, food, date));
    }
}
