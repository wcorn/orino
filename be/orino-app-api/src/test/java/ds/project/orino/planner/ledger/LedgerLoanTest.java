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
import static org.hamcrest.Matchers.hasItem;
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
 * 대출 자산과 잔여 원금(#1400).
 *
 * <p>확인하는 것은 <b>원금이 어디로 새지 않는가</b>다 — 잔여 원금은 부채로만 가고 총자산에서 빠지지
 * 않으며, 원금 이체는 지출 합계에 들어가지 않고, 대출에는 이체 말고 아무것도 붙지 않는다.
 *
 * <p>오늘은 2026-09-13이다. 기준일 경계를 보는 테스트라 시각을 못박는다.
 */
@FixedClock("2026-09-13T03:00:00Z")
class LedgerLoanTest extends ApiTestSupport {

    private static final long BASELINE = 98_200_000L;

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
                {"type": "INCOME", "amount": 200000000, "assetId": %d, "occurredOn": "2026-01-02"}
                """.formatted(checking));
    }

    @Nested
    @DisplayName("만들기")
    class Open {

        @Test
        @DisplayName("대출 속성과 금리 첫 행이 함께 생기고, 잔액이 아니라 잔여 원금을 갖는다")
        void opensWithFirstRate() throws Exception {
            long loan = loan(checking, "2025-03-25", "2027-03-25", "3.850", "2026-09-01");

            mockMvc.perform(get("/api/ledger/loans/" + loan)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.principalRemaining").value(BASELINE))
                    .andExpect(jsonPath("$.data.repaymentMethod").value("EQUAL_PAYMENT"))
                    .andExpect(jsonPath("$.data.paymentAssetName").value("급여통장"))
                    .andExpect(jsonPath("$.data.baseline.asOf").value("2026-09-01"))
                    .andExpect(jsonPath("$.data.currentRate").value(3.85))
                    // 첫 금리는 실행일부터다.
                    .andExpect(jsonPath("$.data.rates", hasSize(1)))
                    .andExpect(jsonPath("$.data.rates[0].effectiveFrom").value("2025-03-25"));

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.groups[0].assets[?(@.name=='전세자금대출')].balance")
                            .value(contains(nullValue())))
                    .andExpect(jsonPath("$.data.groups[0].assets[?(@.name=='전세자금대출')].principalRemaining")
                            .value(contains((int) BASELINE)));
        }

        @Test
        @DisplayName("대출인데 대출 속성이 없으면 거부한다")
        void requiresLoanBlock() throws Exception {
            createAsset("{\"name\": \"대출\", \"type\": \"LOAN\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-041"));
        }

        @Test
        @DisplayName("대출이 아닌 자산에 대출 속성을 붙이면 거부한다 — 블록은 유형과 맞아야 한다")
        void rejectsLoanBlockOnOtherType() throws Exception {
            createAsset("""
                    {"name": "통장", "type": "CHECKING", "loan": %s}
                    """.formatted(block(checking, "2025-03-25", "2027-03-25", "3.850", "2026-09-01")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-041"));
        }

        @Test
        @DisplayName("만기가 실행일보다 앞서면 거부한다")
        void rejectsMaturityBeforeStart() throws Exception {
            createLoan(block(checking, "2027-03-25", "2025-03-25", "3.850", "2026-09-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-041"));
        }

        @Test
        @DisplayName("출금 계좌가 잔액을 갖지 않으면 거부한다 — 카드에서 원금이 빠질 수는 없다")
        void rejectsCardAsPaymentAccount() throws Exception {
            long card = LedgerFixture.createAsset(mockMvc, authHeader, "신용카드", "CREDIT_CARD");

            createLoan(block(card, "2025-03-25", "2027-03-25", "3.850", "2026-09-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-041"));
        }

        @Test
        @DisplayName("금리가 없으면 거부한다")
        void rejectsMissingRate() throws Exception {
            createLoan(block(checking, "2025-03-25", "2027-03-25", "null", "2026-09-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-041"));
        }

        @Test
        @DisplayName("기준일이 미래면 거부한다 — 은행 앱이 보여줄 수 없는 값이다")
        void rejectsFutureBaseline() throws Exception {
            createLoan(block(checking, "2025-03-25", "2027-03-25", "3.850", "2026-09-14"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-045"));

            // 거부되면 자산도 남지 않는다 — 대출 속성 없는 대출 자산은 아무 말도 아니다.
            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.groups[0].assets", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("잔여 원금")
    class Principal {

        @Test
        @DisplayName("기준 원금 + 기준일 이후 이체 — 기준일 당일 이체는 이미 기준 원금에 들어 있다")
        void baselinePlusTransfersAfterAsOf() throws Exception {
            long loan = loan(checking, "2025-03-25", "2027-03-25", "3.850", "2026-09-01");
            transfer(checking, loan, 1_000_000, "2026-09-01");
            transfer(checking, loan, 2_000_000, "2026-09-05");
            // 대출에서 나간 이체는 실행이다 — 원금이 는다.
            transfer(loan, checking, 500_000, "2026-09-10");

            mockMvc.perform(get("/api/ledger/loans/" + loan)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    // 98,200,000 − 2,000,000 + 500,000
                    .andExpect(jsonPath("$.data.principalRemaining").value(96_700_000));
        }

        @Test
        @DisplayName("잔여 원금은 부채로만 간다 — 총자산에서 빼지 않고, 그룹 합계는 빚을 뺀다")
        void principalIsLiability() throws Exception {
            long loan = loan(checking, "2025-03-25", "2027-03-25", "3.850", "2026-09-01");
            transfer(checking, loan, 1_000_000, "2026-09-01");
            transfer(checking, loan, 2_000_000, "2026-09-05");
            transfer(loan, checking, 500_000, "2026-09-10");

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    // 급여통장 200,000,000 − 1,000,000 − 2,000,000 + 500,000
                    .andExpect(jsonPath("$.data.totalAssets").value(197_500_000))
                    .andExpect(jsonPath("$.data.liabilities").value(96_700_000))
                    .andExpect(jsonPath("$.data.liabilityBreakdown.card").value(0))
                    .andExpect(jsonPath("$.data.liabilityBreakdown.loan").value(96_700_000))
                    .andExpect(jsonPath("$.data.netWorth").value(100_800_000))
                    .andExpect(jsonPath("$.data.groups[0].subtotal").value(100_800_000));

            mockMvc.perform(get("/api/ledger/dashboard")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.netWorth.liabilities").value(96_700_000));
        }

        @Test
        @DisplayName("원금 이체는 지출이 아니다 — 이번 달 지출 합계에 들어가지 않는다")
        void principalTransferIsNotSpending() throws Exception {
            long loan = loan(checking, "2025-03-25", "2027-03-25", "3.850", "2026-09-01");
            transfer(checking, loan, 2_000_000, "2026-09-05");

            mockMvc.perform(get("/api/ledger/summary")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.monthSpent").value(0));
        }
    }

    @Nested
    @DisplayName("대출에 붙는 거래")
    class TransferOnly {

        @Test
        @DisplayName("대출에 지출·수입을 붙이면 거부하고, 이체는 받는다")
        void rejectsNonTransfer() throws Exception {
            long loan = loan(checking, "2025-03-25", "2027-03-25", "3.850", "2026-09-01");

            createTransaction("""
                    {"type": "EXPENSE", "amount": 300000, "assetId": %d, "occurredOn": "2026-09-10"}
                    """.formatted(loan))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-039"));
            createTransaction("""
                    {"type": "INCOME", "amount": 300000, "assetId": %d, "occurredOn": "2026-09-10"}
                    """.formatted(loan))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-039"));
            createTransaction("""
                    {"type": "TRANSFER", "amount": 300000, "assetId": %d,
                     "counterAssetId": %d, "occurredOn": "2026-09-10"}
                    """.formatted(checking, loan))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("대출 이체를 수입으로 고쳐도 거부한다 — 수정도 같은 구멍이다")
        void rejectsTypeChangeOnLoan() throws Exception {
            long loan = loan(checking, "2025-03-25", "2027-03-25", "3.850", "2026-09-01");
            long id = LedgerFixture.transactionId(transfer(loan, checking, 500_000, "2026-09-10"));

            mockMvc.perform(patch("/api/ledger/transactions/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\": \"INCOME\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-039"));
        }

        @Test
        @DisplayName("대출로 들어가거나 나가는 정기 항목은 만들 수 없다 — 상환은 회차 확정 한 길뿐이다")
        void rejectsRecurringOnLoan() throws Exception {
            long loan = loan(checking, "2025-03-25", "2027-03-25", "3.850", "2026-09-01");

            createRecurring(checking, loan)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-046"));
            createRecurring(loan, checking)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-046"));
        }
    }

    @Nested
    @DisplayName("기준 원금 · 금리")
    class BaselineAndRate {

        @Test
        @DisplayName("기준 원금을 다시 맞추면 직전 원장 추정을 함께 돌려준다 — 거래는 만들지 않는다")
        void rebaselineReturnsBefore() throws Exception {
            long loan = loan(checking, "2025-03-25", "2027-03-25", "3.850", "2026-09-01");
            transfer(checking, loan, 1_500_000, "2026-09-05");

            mockMvc.perform(put("/api/ledger/loans/%d/baseline".formatted(loan))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"principal\": 96690000, \"asOf\": \"2026-09-13\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.before").value(96_700_000))
                    .andExpect(jsonPath("$.data.after").value(96_690_000));

            // 차이 10,000원은 원장에 적히지 않는다 — 조정 거래가 생기면 그 돈이 어느 달의 수입이 된다.
            mockMvc.perform(get("/api/ledger/assets/%d/transactions".formatted(loan))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.items", hasSize(1)));
        }

        @Test
        @DisplayName("기준 원금이 음수이거나 기준일이 미래면 거부한다")
        void rejectsInvalidBaseline() throws Exception {
            long loan = loan(checking, "2025-03-25", "2027-03-25", "3.850", "2026-09-01");

            putBaseline(loan, "{\"principal\": -1}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-045"));
            putBaseline(loan, "{\"principal\": 90000000, \"asOf\": \"2026-09-14\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-045"));
        }

        @Test
        @DisplayName("금리 변경은 한 줄씩 더하고, 같은 적용일이면 그 줄을 고친다")
        void addsRateRows() throws Exception {
            long loan = loan(checking, "2025-03-25", "2027-03-25", "3.850", "2026-09-01");

            postRate(loan, "2026-10-01", "4.100")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].effectiveFrom").value("2026-10-01"))
                    .andExpect(jsonPath("$.data[0].annualRate").value(4.1));
            postRate(loan, "2026-10-01", "4.200")
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].annualRate").value(4.2));

            // 10월 1일은 아직 안 왔다 — 오늘 금리는 그대로다.
            mockMvc.perform(get("/api/ledger/loans/" + loan)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.currentRate").value(3.85));
        }

        @Test
        @DisplayName("대출이 아닌 자산은 대출로 보지 않는다")
        void nonLoanIsNotFound() throws Exception {
            mockMvc.perform(get("/api/ledger/loans/" + checking)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-001"));
            postRate(checking, "2026-10-01", "4.100")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-001"));
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("아무것도 적지 않은 대출은 속성·금리 이력과 함께 지워진다")
        void deletesUntouchedLoan() throws Exception {
            long loan = loan(checking, "2025-03-25", "2027-03-25", "3.850", "2026-09-01");

            mockMvc.perform(delete("/api/ledger/assets/" + loan)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/ledger/loans/" + loan)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("대출의 출금 계좌는 지울 수 없다 — 원금이 빠질 곳이 사라진다")
        void keepsPaymentAccount() throws Exception {
            long account = LedgerFixture.createAsset(mockMvc, authHeader, "출금통장", "CHECKING");
            loan(account, "2025-03-25", "2027-03-25", "3.850", "2026-09-01");

            mockMvc.perform(get("/api/ledger/assets/" + account)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.deletable").value(false))
                    .andExpect(jsonPath("$.data.deleteBlockers", hasItem("LINKED_ASSET")));
            mockMvc.perform(delete("/api/ledger/assets/" + account)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-034"));
        }
    }

    // --- 준비 ---

    private String block(long paymentAssetId, String startedOn, String maturityDate,
                         String annualRate, String baselineAsOf) {
        return """
                {"repaymentMethod": "EQUAL_PAYMENT", "paymentAssetId": %d, "paymentDay": 25,
                 "businessDayPolicy": "NEXT", "startedOn": "%s", "maturityDate": "%s",
                 "originalPrincipal": 150000000, "annualRate": %s,
                 "baselinePrincipal": %d, "baselineAsOf": "%s"}
                """.formatted(paymentAssetId, startedOn, maturityDate, annualRate, BASELINE, baselineAsOf);
    }

    private long loan(long paymentAssetId, String startedOn, String maturityDate,
                      String annualRate, String baselineAsOf) throws Exception {
        String body = createLoan(block(paymentAssetId, startedOn, maturityDate, annualRate, baselineAsOf))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private ResultActions createLoan(String block) throws Exception {
        return createAsset("""
                {"name": "전세자금대출", "type": "LOAN", "loan": %s}
                """.formatted(block));
    }

    private ResultActions createAsset(String json) throws Exception {
        return mockMvc.perform(post("/api/ledger/assets")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions createTransaction(String json) throws Exception {
        return mockMvc.perform(post("/api/ledger/transactions")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String transfer(long from, long to, long amount, String occurredOn) throws Exception {
        return LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "TRANSFER", "amount": %d, "assetId": %d,
                 "counterAssetId": %d, "occurredOn": "%s"}
                """.formatted(amount, from, to, occurredOn));
    }

    private ResultActions createRecurring(long from, long to) throws Exception {
        return mockMvc.perform(post("/api/ledger/recurring")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "대출 상환", "kind": "TRANSFER", "txType": "TRANSFER",
                         "amount": 4515900, "assetId": %d, "counterAssetId": %d,
                         "freqType": "MONTHLY_DAY", "freqDay": 25,
                         "startDate": "2026-09-25"}
                        """.formatted(from, to)));
    }

    private ResultActions putBaseline(long loan, String json) throws Exception {
        return mockMvc.perform(put("/api/ledger/loans/%d/baseline".formatted(loan))
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions postRate(long assetId, String effectiveFrom, String annualRate)
            throws Exception {
        return mockMvc.perform(post("/api/ledger/rates")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"assetId": %d, "effectiveFrom": "%s", "annualRate": %s}
                        """.formatted(assetId, effectiveFrom, annualRate)));
    }
}
