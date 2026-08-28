package ds.project.orino.planner.ledger;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
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

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 원장의 불변 조건(#1259).
 *
 * <p>이 테스트가 고정하는 것은 <b>이 모듈이 틀리면 안 되는 것들</b>이다 — 자산에 붙는 거래,
 * 지출 합계에 섞이지 않는 이체, 미래 날짜의 예정 강제, 지우지 않는 상쇄. 넷 중 하나라도
 * 무너지면 「이번 달 얼마 쓰게 되고 월말에 얼마 남나」에 답할 수 없다.
 */
@Import(StubExternalsConfig.class)
class LedgerTransactionControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private long checking;
    private long savings;
    private long foodCategory;
    private long salaryCategory;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);

        checking = LedgerFixture.createAsset(mockMvc, authHeader, "급여통장", "CHECKING");
        savings = LedgerFixture.createAsset(mockMvc, authHeader, "비상금", "SAVINGS");
        foodCategory = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "식비");
        salaryCategory = LedgerFixture.categoryIdByName(mockMvc, authHeader, "INCOME", "급여");
    }

    @Nested
    @DisplayName("불변 조건")
    class Invariants {

        @Test
        @DisplayName("자산 없는 거래는 만들 수 없다")
        void rejectsTransactionWithoutAsset() throws Exception {
            mockMvc.perform(post("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type": "EXPENSE", "amount": 4500,
                                     "occurredOn": "%s"}
                                    """.formatted(today())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-002"));
        }

        @Test
        @DisplayName("남의 자산에는 거래를 붙일 수 없다 — 없는 자산과 같이 답한다")
        void rejectsUnknownAsset() throws Exception {
            mockMvc.perform(post("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type": "EXPENSE", "amount": 4500, "assetId": 999999,
                                     "occurredOn": "%s"}
                                    """.formatted(today())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-001"));
        }

        @Test
        @DisplayName("이체는 대상 자산이 있어야 한다")
        void rejectsTransferWithoutCounterAsset() throws Exception {
            mockMvc.perform(post("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type": "TRANSFER", "amount": 100000, "assetId": %d,
                                     "occurredOn": "%s"}
                                    """.formatted(checking, today())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-004"));
        }

        @Test
        @DisplayName("출금과 입금 자산이 같은 이체는 거부한다")
        void rejectsTransferToSameAsset() throws Exception {
            mockMvc.perform(post("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type": "TRANSFER", "amount": 100000, "assetId": %d,
                                     "counterAssetId": %d, "occurredOn": "%s"}
                                    """.formatted(checking, checking, today())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-003"));
        }

        @Test
        @DisplayName("지출에 수입 카테고리를 달 수 없다")
        void rejectsCategoryFlowMismatch() throws Exception {
            mockMvc.perform(post("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type": "EXPENSE", "amount": 4500, "assetId": %d,
                                     "categoryId": %d, "occurredOn": "%s"}
                                    """.formatted(checking, salaryCategory, today())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-005"));
        }

        @Test
        @DisplayName("카테고리 없이도 저장된다 — 기록을 막느니 나중에 채운다")
        void allowsUncategorized() throws Exception {
            mockMvc.perform(post("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type": "EXPENSE", "amount": 4500, "assetId": %d,
                                     "occurredOn": "%s"}
                                    """.formatted(checking, today())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.transaction.categoryId").doesNotExist());
        }

        @Test
        @DisplayName("미래 날짜는 예정으로 저장되고 응답이 그 사실을 알린다")
        void futureDateBecomesScheduled() throws Exception {
            mockMvc.perform(post("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type": "EXPENSE", "amount": 17000, "assetId": %d,
                                     "categoryId": %d, "occurredOn": "%s"}
                                    """.formatted(checking, foodCategory, today().plusDays(5))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.savedAs").value("SCHEDULED"))
                    .andExpect(jsonPath("$.data.transaction.status").value("SCHEDULED"));
        }

        @Test
        @DisplayName("이체는 지출에도 수입에도 잡히지 않는다 — 카드 대금이 새는 구멍을 막는다")
        void transferNeverCountsAsSpending() throws Exception {
            expense(4500);
            income(3000000);
            transfer(500000);

            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.monthTotals.expense").value(4500))
                    .andExpect(jsonPath("$.data.monthTotals.income").value(3000000))
                    .andExpect(jsonPath("$.data.monthTotals.transfer").value(500000));
        }

        @Test
        @DisplayName("삭제한 거래는 목록에서도 합계에서도 빠진다 — 행은 남는다")
        void softDeleteRemovesFromTotals() throws Exception {
            long id = LedgerFixture.transactionId(expense(4500));

            mockMvc.perform(delete("/api/ledger/transactions/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.monthTotals.expense").value(0))
                    .andExpect(jsonPath("$.data.groups").isEmpty());

            // 되돌리기가 가능해야 하므로 조회 자체는 404다(살아 있는 것만 준다).
            mockMvc.perform(get("/api/ledger/transactions/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-006"));
        }
    }

    @Nested
    @DisplayName("상쇄 — 지우지 않는다")
    class Refund {

        @Test
        @DisplayName("환불해도 원 거래는 그대로 남는다")
        void keepsOriginalTransaction() throws Exception {
            long id = LedgerFixture.transactionId(expense(30000));

            mockMvc.perform(post("/api/ledger/transactions/%d/refund".formatted(id))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.refund.refundOfId").value((int) id))
                    .andExpect(jsonPath("$.data.remaining").value(0));

            mockMvc.perform(get("/api/ledger/transactions/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.amount").value(30000));
        }

        @Test
        @DisplayName("전액 환불하면 지출 합계가 0이 된다 — 수입이 늘지 않는다")
        void refundReducesExpenseNotIncome() throws Exception {
            long id = LedgerFixture.transactionId(expense(30000));
            refund(id, null);

            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.monthTotals.expense").value(0))
                    .andExpect(jsonPath("$.data.monthTotals.income").value(0));
        }

        @Test
        @DisplayName("부분 환불은 그만큼만 깎고 남은 금액을 알려준다")
        void partialRefund() throws Exception {
            long id = LedgerFixture.transactionId(expense(30000));

            mockMvc.perform(post("/api/ledger/transactions/%d/refund".formatted(id))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\": 12000}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.refundedTotal").value(12000))
                    .andExpect(jsonPath("$.data.remaining").value(18000));

            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.monthTotals.expense").value(18000));
        }

        @Test
        @DisplayName("남은 금액보다 많이 환불할 수 없다")
        void rejectsOverRefund() throws Exception {
            long id = LedgerFixture.transactionId(expense(30000));
            refund(id, 25000L);

            mockMvc.perform(post("/api/ledger/transactions/%d/refund".formatted(id))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\": 10000}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("환불분은 원 거래의 카테고리를 물려받는다 — 그 카테고리의 지출이 준 것이다")
        void refundInheritsCategory() throws Exception {
            long id = LedgerFixture.transactionId(expense(30000));

            mockMvc.perform(post("/api/ledger/transactions/%d/refund".formatted(id))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(jsonPath("$.data.refund.categoryId").value((int) foodCategory))
                    .andExpect(jsonPath("$.data.refund.type").value("INCOME"))
                    .andExpect(jsonPath("$.data.refund.source").value("REFUND"));
        }
    }

    @Nested
    @DisplayName("일괄 처리와 자동완성")
    class BulkAndSuggest {

        @Test
        @DisplayName("미분류 여러 건에 카테고리를 한 번에 붙인다")
        void bulkSetsCategory() throws Exception {
            long first = LedgerFixture.transactionId(uncategorized(1000));
            long second = LedgerFixture.transactionId(uncategorized(2000));

            mockMvc.perform(post("/api/ledger/transactions/bulk")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"action": "SET_CATEGORY", "ids": [%d, %d], "categoryId": %d}
                                    """.formatted(first, second, foodCategory)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.affected").value(2));

            mockMvc.perform(get("/api/ledger/transactions/" + first)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.categoryId").value((int) foodCategory));
        }

        @Test
        @DisplayName("유형이 맞지 않는 건이 섞이면 통째로 거부한다 — 일부만 적용되지 않는다")
        void bulkRejectsFlowMismatch() throws Exception {
            long expenseId = LedgerFixture.transactionId(uncategorized(1000));
            long incomeId = LedgerFixture.transactionId(income(2000));

            mockMvc.perform(post("/api/ledger/transactions/bulk")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"action": "SET_CATEGORY", "ids": [%d, %d], "categoryId": %d}
                                    """.formatted(expenseId, incomeId, foodCategory)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-005"));

            mockMvc.perform(get("/api/ledger/transactions/" + expenseId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.categoryId").doesNotExist());
        }

        @Test
        @DisplayName("같은 내용을 다시 적으면 지난번 카테고리·자산을 제안한다")
        void suggestsFromHistory() throws Exception {
            mockMvc.perform(post("/api/ledger/transactions")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"type": "EXPENSE", "amount": 4500, "assetId": %d,
                             "categoryId": %d, "title": "스타벅스 역삼", "occurredOn": "%s"}
                            """.formatted(checking, foodCategory, today())));

            mockMvc.perform(get("/api/ledger/transactions/suggest")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("q", "스타벅스"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].title").value("스타벅스 역삼"))
                    .andExpect(jsonPath("$.data[0].categoryId").value((int) foodCategory))
                    .andExpect(jsonPath("$.data[0].assetId").value((int) checking));
        }

        @Test
        @DisplayName("태그는 이름으로 붙고 다시 쓰면 재사용된다")
        void tagsAreReused() throws Exception {
            mockMvc.perform(post("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type": "EXPENSE", "amount": 4500, "assetId": %d,
                                     "tags": ["회사", "점심"], "occurredOn": "%s"}
                                    """.formatted(checking, today())))
                    .andExpect(jsonPath("$.data.transaction.tags", org.hamcrest.Matchers.hasSize(2)));

            mockMvc.perform(post("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type": "EXPENSE", "amount": 6000, "assetId": %d,
                                     "tags": ["회사"], "occurredOn": "%s"}
                                    """.formatted(checking, today())))
                    .andExpect(jsonPath("$.data.transaction.tags[0]").value("회사"));
        }
    }

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        @DisplayName("날짜를 미래로 밀면 예정이 되고 과거로 당기면 확정이 된다")
        void statusFollowsDate() throws Exception {
            long id = LedgerFixture.transactionId(expense(4500));

            mockMvc.perform(patch("/api/ledger/transactions/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"occurredOn\": \"%s\"}".formatted(today().plusDays(3))))
                    .andExpect(jsonPath("$.data.status").value("SCHEDULED"));

            mockMvc.perform(patch("/api/ledger/transactions/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"occurredOn\": \"%s\"}".formatted(today())))
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        }

        @Test
        @DisplayName("미분류로 되돌릴 수 있다")
        void clearsCategory() throws Exception {
            long id = LedgerFixture.transactionId(expense(4500));

            mockMvc.perform(patch("/api/ledger/transactions/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"clearCategory\": true}"))
                    .andExpect(jsonPath("$.data.categoryId").doesNotExist());
        }
    }

    // --- 준비 ---

    private LocalDate today() {
        return LocalDate.now(TEST_ZONE);
    }

    private String expense(long amount) throws Exception {
        return LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": %d, "assetId": %d,
                 "categoryId": %d, "occurredOn": "%s"}
                """.formatted(amount, checking, foodCategory, today()));
    }

    private String uncategorized(long amount) throws Exception {
        return LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": %d, "assetId": %d, "occurredOn": "%s"}
                """.formatted(amount, checking, today()));
    }

    private String income(long amount) throws Exception {
        return LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "INCOME", "amount": %d, "assetId": %d,
                 "categoryId": %d, "occurredOn": "%s"}
                """.formatted(amount, checking, salaryCategory, today()));
    }

    private String transfer(long amount) throws Exception {
        return LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "TRANSFER", "amount": %d, "assetId": %d,
                 "counterAssetId": %d, "occurredOn": "%s"}
                """.formatted(amount, checking, savings, today()));
    }

    private void refund(long id, Long amount) throws Exception {
        String body = amount == null ? "{}" : "{\"amount\": %d}".formatted(amount);
        mockMvc.perform(post("/api/ledger/transactions/%d/refund".formatted(id))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
