package ds.project.orino.planner.ledger;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.planner.ledger.card.StatementCycleScheduler;
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

import java.time.Clock;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 카드 청구서(#1262) — <b>이 모듈의 심장</b>.
 *
 * <p>여기서 확인하는 것은 기능이 도는지가 아니라 <b>수동 가계부가 무너지는 지점을 막았는지</b>다:
 * 카드 대금이 지출로 새지 않는가, 이월을 두 번 세지 않는가, 할부 잔여가 부채로 잡히는가.
 *
 * <p>시계를 못박는다(2026-01-15). 사이클 경계를 단언하는 테스트라 실시각으로는 매달 다른 결과가 난다.
 */
@FixedClock
class LedgerCardStatementTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private Clock clock;
    @Autowired
    private StatementCycleScheduler cycleScheduler;

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
        card = LedgerFixture.createAsset(mockMvc, authHeader, "신한 Deep Dream", "CREDIT_CARD");
        food = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "식비");

        // 1일~말일 사용 → 익월 14일 결제.
        mockMvc.perform(patch("/api/ledger/cards/%d/cycle".formatted(card))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cycleStartDay": 1, "cycleCloseDay": 99, "paymentDay": 14,
                                 "paymentAssetId": %d}
                                """.formatted(checking)))
                .andExpect(status().isOk());
    }

    @Nested
    @DisplayName("사이클 편입")
    class Cycle {

        @Test
        @DisplayName("카드로 쓴 건이 그 달 청구서에 편입된다")
        void usageJoinsCycle() throws Exception {
            cardExpense(180000, today());

            mockMvc.perform(get("/api/ledger/cards/%d/statements".formatted(card))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].breakdown.usage").value(180000))
                    .andExpect(jsonPath("$.data[0].breakdown.billed").value(180000))
                    // 1월 사용 → 2월 14일 결제.
                    .andExpect(jsonPath("$.data[0].paymentDate").value("2026-02-13"));
        }

        @Test
        @DisplayName("사이클이 없는 카드는 청구서를 만들지 않는다 — 오류가 아니라 아직 등록 전이다")
        void noCycleNoStatement() throws Exception {
            long plain = LedgerFixture.createAsset(
                    mockMvc, authHeader, "사이클 미등록", "CREDIT_CARD");
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 50000, "assetId": %d, "occurredOn": "%s"}
                    """.formatted(plain, today()));

            mockMvc.perform(get("/api/ledger/cards/%d/statements".formatted(plain))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("마감이 지나면 확정되고 다음 사이클이 열린다")
        void schedulerRollsCycle() throws Exception {
            // 지난달 사용 → 그 사이클은 이미 마감됐다.
            cardExpense(100000, today().minusMonths(1));

            cycleScheduler.rollCycles();

            mockMvc.perform(get("/api/ledger/cards/%d/statements".formatted(card))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    // 최근 사이클부터 온다.
                    .andExpect(jsonPath("$.data[0].status").value("COLLECTING"))
                    .andExpect(jsonPath("$.data[1].status").value("CONFIRMED"));
        }
    }

    @Nested
    @DisplayName("카드 대금은 지출이 아니다")
    class Payment {

        @Test
        @DisplayName("결제 처리로 만들어진 거래는 이체이고 월 지출에 안 들어간다")
        void paymentIsTransferNotSpending() throws Exception {
            cardExpense(180000, today());
            long statementId = firstStatementId();

            mockMvc.perform(post("/api/ledger/statements/%d/pay".formatted(statementId))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PAID"));

            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("from", today().withDayOfMonth(1).toString())
                            .param("to", today().plusMonths(2).toString()))
                    // 지출은 카드 사용 18만뿐이다. 대금 18만이 더해지면 36만이 된다 —
                    // 그게 이 모듈에서 가장 자주 일어나는 오류다.
                    .andExpect(jsonPath("$.data.monthTotals.expense").value(180000))
                    .andExpect(jsonPath("$.data.monthTotals.transfer").value(180000));
        }

        @Test
        @DisplayName("전액 납부하면 카드 빚이 0이 된다")
        void fullPaymentClearsDebt() throws Exception {
            cardExpense(180000, today());
            income(1000000);
            pay(firstStatementId(), null);

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.liabilities").value(0));
        }

        @Test
        @DisplayName("이미 납부한 청구서는 다시 결제할 수 없다")
        void rejectsDoublePayment() throws Exception {
            cardExpense(180000, today());
            long statementId = firstStatementId();
            pay(statementId, null);

            mockMvc.perform(post("/api/ledger/statements/%d/pay".formatted(statementId))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-008"));
        }

        @Test
        @DisplayName("청구액보다 많이 낼 수 없다")
        void rejectsOverpayment() throws Exception {
            cardExpense(180000, today());

            mockMvc.perform(post("/api/ledger/statements/%d/pay".formatted(firstStatementId()))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\": 200000}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-009"));
        }
    }

    @Nested
    @DisplayName("부분 납부와 이월")
    class CarryOver {

        @Test
        @DisplayName("일부만 내면 잔액이 다음 청구서에 별도 항목으로 얹힌다")
        void partialPaymentCarriesOver() throws Exception {
            cardExpense(180000, today());
            long statementId = firstStatementId();

            mockMvc.perform(post("/api/ledger/statements/%d/pay".formatted(statementId))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\": 100000}"))
                    .andExpect(jsonPath("$.data.status").value("PARTIAL"))
                    .andExpect(jsonPath("$.data.breakdown.remaining").value(80000))
                    .andExpect(jsonPath("$.data.carriedToStatementId").isNumber());

            mockMvc.perform(get("/api/ledger/cards/%d/statements".formatted(card))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    // 다음 청구서에 이월이 별도 항목으로 들어가 있다 — 사용액과 섞이지 않는다.
                    .andExpect(jsonPath("$.data[0].breakdown.carriedOver").value(80000))
                    .andExpect(jsonPath("$.data[0].breakdown.usage").value(0));
        }

        @Test
        @DisplayName("이월은 지출 합계에 들어가지 않는다 — 같은 돈을 두 번 세지 않는다")
        void carryOverIsNotSpending() throws Exception {
            cardExpense(180000, today());
            pay(firstStatementId(), 100000L);

            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("from", today().withDayOfMonth(1).toString())
                            .param("to", today().plusMonths(3).toString()))
                    // 쓴 돈은 18만뿐이다. 이월 8만이 더해지면 26만이 되고, 그건 거짓이다 —
                    // 이미 쓸 때 잡혔고 갚는 행위는 지출이 아니다.
                    .andExpect(jsonPath("$.data.monthTotals.expense").value(180000));
        }

        @Test
        @DisplayName("남은 잔액은 부채로 계속 잡힌다")
        void remainderStaysAsDebt() throws Exception {
            cardExpense(180000, today());
            income(1000000);
            pay(firstStatementId(), 100000L);

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.liabilities").value(80000));
        }

        @Test
        @DisplayName("수수료만 새 지출이 될 자리를 갖는다 — 이월과 섞이지 않는다")
        void interestFeeIsSeparate() throws Exception {
            cardExpense(180000, today());
            long statementId = firstStatementId();

            mockMvc.perform(post("/api/ledger/statements/%d/adjust".formatted(statementId))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"interestFeeAmount": 12000, "adjustmentAmount": 3000,
                                     "adjustmentCategoryId": %d}
                                    """.formatted(food)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.breakdown.interestFee").value(12000))
                    .andExpect(jsonPath("$.data.breakdown.adjustment").value(3000))
                    .andExpect(jsonPath("$.data.breakdown.billed").value(195000));
        }

        @Test
        @DisplayName("수수료만 새 지출이 된다 — 이월은 아니다")
        void onlyFeeBecomesSpending() throws Exception {
            cardExpense(180000, today());
            long statementId = firstStatementId();

            mockMvc.perform(post("/api/ledger/statements/%d/adjust".formatted(statementId))
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"interestFeeAmount\": 12000}"));
            // 일부만 내서 이월이 생긴다.
            pay(statementId, 100000L);

            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("from", today().withDayOfMonth(1).toString())
                            .param("to", today().plusMonths(3).toString()))
                    // 사용 180,000 + 수수료 12,000. 이월 92,000은 들어가지 않는다 —
                    // 「왜 갚아도 안 줄지」는 수수료가 답하고, 이월은 이미 쓸 때 잡혔다.
                    .andExpect(jsonPath("$.data.monthTotals.expense").value(192000));

            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("from", today().withDayOfMonth(1).toString())
                            .param("to", today().plusMonths(3).toString()))
                    .andExpect(jsonPath(
                            "$.data.groups[*].items[?(@.title == '카드 이자·수수료')].categoryName")
                            .value(org.hamcrest.Matchers.contains("이자/수수료")));
        }
    }

    @Nested
    @DisplayName("할부")
    class Installment {

        @Test
        @DisplayName("원 거래에 전액이 적히고 회차가 나뉜다 — 나머지는 첫 회차가 받는다")
        void splitsIntoRounds() throws Exception {
            String body = LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 100000, "assetId": %d,
                     "categoryId": %d, "occurredOn": "%s",
                     "installment": {"months": 3, "interestFree": true}}
                    """.formatted(card, food, today()));

            // 소비 관점은 원 거래의 전액을 본다.
            assertAmount(body, 100000);

            // 100,000 ÷ 3 = 33,333 … 나머지 1은 첫 회차로.
            mockMvc.perform(get("/api/ledger/cards")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.installmentOutstanding").value(100000));
        }

        /**
         * 원 거래 <b>전액</b>은 사용 합계에서 빠지고, 그 달에 청구되는 것은 <b>1회차뿐</b>이다.
         *
         * <p>전액을 사용에 넣으면 회차로 또 청구되어 같은 물건값을 두 번 받고, 1회차를
         * 빼 버리면 카드사가 실제로 청구하는 금액보다 적게 보인다(#1279).
         */
        @Test
        @DisplayName("원 거래는 사용에서 빠지고 1회차만 그 달에 청구된다")
        void billsFirstRoundOnly() throws Exception {
            cardExpense(180000, today());
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 100000, "assetId": %d,
                     "occurredOn": "%s", "installment": {"months": 3}}
                    """.formatted(card, today()));

            mockMvc.perform(get("/api/ledger/cards/%d/statements".formatted(card))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data[0].breakdown.usage").value(180000))
                    // 100,000 ÷ 3 = 33,333 … 나머지 1은 첫 회차로 → 33,334.
                    .andExpect(jsonPath("$.data[0].breakdown.installment").value(33334))
                    .andExpect(jsonPath("$.data[0].breakdown.billed").value(213334));
        }

        /**
         * 사이클 전환은 <b>다음 사이클을 열 때</b>만 회차를 붙인다. 1회차가 붙어야 할 청구서는
         * 카드를 긁는 순간 이미 서 있으므로, 전환에 맡기면 영영 안 붙는다.
         */
        @Test
        @DisplayName("사이클이 넘어가도 1회차의 청구서는 바뀌지 않는다")
        void firstRoundKeepsItsStatement() throws Exception {
            // 지난달에 샀다 — 그래야 사이클 전환이 실제로 일어난다.
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 100000, "assetId": %d,
                     "occurredOn": "%s", "installment": {"months": 3}}
                    """.formatted(card, today().minusMonths(1)));

            cycleScheduler.rollCycles();

            // 산 달 청구서는 1회차 그대로, 새로 열린 청구서는 2회차만.
            mockMvc.perform(get("/api/ledger/cards/%d/statements".formatted(card))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data[1].breakdown.installment").value(33334))
                    .andExpect(jsonPath("$.data[0].breakdown.installment").value(33333));
        }

        @Test
        @DisplayName("잔여 원금이 부채로 잡힌다 — 아직 청구되지 않은 회차도 이미 빚이다")
        void outstandingIsDebt() throws Exception {
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 600000, "assetId": %d,
                     "occurredOn": "%s", "installment": {"months": 6}}
                    """.formatted(card, today()));

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    // 카드의 미결제 사용액이 전액을 담는다. 회차를 또 더하면 빚이 두 배로 보인다.
                    .andExpect(jsonPath("$.data.liabilities").value(600000));
        }

        @Test
        @DisplayName("개월 수가 범위 밖이면 거부한다")
        void rejectsOutOfRangeMonths() throws Exception {
            mockMvc.perform(post("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type": "EXPENSE", "amount": 600000, "assetId": %d,
                                     "occurredOn": "%s", "installment": {"months": 1}}
                                    """.formatted(card, today())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-016"));
        }
    }

    @Nested
    @DisplayName("미납")
    class Overdue {

        @Test
        @DisplayName("결제일이 지났는데 안 냈으면 미납이다 — 저장된 플래그가 아니다")
        void derivesOverdue() throws Exception {
            // 두 달 전 사용 → 결제일이 이미 지났다.
            cardExpense(180000, today().minusMonths(2));
            cycleScheduler.rollCycles();

            mockMvc.perform(get("/api/ledger/cards/%d/statements".formatted(card))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data[?(@.breakdown.usage == 180000)].overdue")
                            .value(org.hamcrest.Matchers.contains(true)));
        }

        @Test
        @DisplayName("결제일이 아직 안 왔으면 미납이 아니다")
        void notOverdueBeforePaymentDate() throws Exception {
            cardExpense(180000, today());

            mockMvc.perform(get("/api/ledger/cards/%d/statements".formatted(card))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data[0].overdue").value(false));
        }
    }

    // --- 준비 ---

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), TEST_ZONE);
    }

    private void cardExpense(long amount, LocalDate on) throws Exception {
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": %d, "assetId": %d,
                 "categoryId": %d, "occurredOn": "%s"}
                """.formatted(amount, card, food, on));
    }

    private void income(long amount) throws Exception {
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "INCOME", "amount": %d, "assetId": %d, "occurredOn": "%s"}
                """.formatted(amount, checking, today()));
    }

    private void pay(long statementId, Long amount) throws Exception {
        String body = amount == null ? "{}" : "{\"amount\": %d}".formatted(amount);
        mockMvc.perform(post("/api/ledger/statements/%d/pay".formatted(statementId))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private long firstStatementId() throws Exception {
        String body = mockMvc.perform(get("/api/ledger/cards/%d/statements".formatted(card))
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data[0].id")).longValue();
    }

    private void assertAmount(String createResponse, long expected) {
        Number amount = JsonPath.read(createResponse, "$.data.transaction.amount");
        org.assertj.core.api.Assertions.assertThat(amount.longValue()).isEqualTo(expected);
    }
}
