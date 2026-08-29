package ds.project.orino.planner.ledger;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionReceiptRepository;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 입력 편의(#1270) — 템플릿 · 복사 · 다건 · 영수증.
 *
 * <p>여기서 가장 중요한 것은 <b>다건 입력의 전부-아니면-전무</b>다. 일부만 들어간 원장은
 * 「어디까지 적었더라」를 사람이 다시 맞춰야 하고, 그건 몰아 적는 이유를 없앤다.
 */
class LedgerInputConvenienceTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private LedgerTransactionReceiptRepository receiptRepository;

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
    @DisplayName("빠른 입력 템플릿")
    class Templates {

        @Test
        @DisplayName("템플릿으로 적으면 오늘 날짜로 저장된다")
        void appliesWithTodayDate() throws Exception {
            long id = createTemplate("출근 커피", 4500);

            mockMvc.perform(post("/api/ledger/templates/%d/apply".formatted(id))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    // 템플릿에는 날짜가 없다 — 언제나 오늘이다.
                    .andExpect(jsonPath("$.data.transaction.occurredOn")
                            .value(today().toString()))
                    .andExpect(jsonPath("$.data.transaction.amount").value(4500))
                    .andExpect(jsonPath("$.data.transaction.categoryId").value((int) food));
        }

        @Test
        @DisplayName("많이 쓴 순으로 온다 — 순서를 사람이 관리하지 않는다")
        void ordersByUseCount() throws Exception {
            long rare = createTemplate("드문 것", 1000);
            long often = createTemplate("자주 쓰는 것", 4500);

            mockMvc.perform(post("/api/ledger/templates/%d/apply".formatted(often))
                    .header(HttpHeaders.AUTHORIZATION, authHeader));
            mockMvc.perform(post("/api/ledger/templates/%d/apply".formatted(often))
                    .header(HttpHeaders.AUTHORIZATION, authHeader));
            mockMvc.perform(post("/api/ledger/templates/%d/apply".formatted(rare))
                    .header(HttpHeaders.AUTHORIZATION, authHeader));

            mockMvc.perform(get("/api/ledger/templates")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data[0].name").value("자주 쓰는 것"))
                    .andExpect(jsonPath("$.data[0].useCount").value(2))
                    .andExpect(jsonPath("$.data[1].useCount").value(1));
        }

        @Test
        @DisplayName("없는 자산으로는 템플릿을 만들 수 없다")
        void rejectsUnknownAsset() throws Exception {
            mockMvc.perform(post("/api/ledger/templates")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "x", "txType": "EXPENSE", "amount": 1000,
                                     "assetId": 999999}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-001"));
        }
    }

    @Nested
    @DisplayName("내역 복사")
    class Duplicate {

        @Test
        @DisplayName("기본은 오늘 날짜다 — 대개 「같은 걸 오늘 또 썼다」이다")
        void copiesToToday() throws Exception {
            long id = LedgerFixture.transactionId(LedgerFixture.createTransaction(
                    mockMvc, authHeader, """
                            {"type": "EXPENSE", "amount": 4500, "assetId": %d,
                             "categoryId": %d, "title": "스타벅스", "occurredOn": "%s"}
                            """.formatted(checking, food, today().minusDays(5))));

            mockMvc.perform(post("/api/ledger/transactions/%d/duplicate".formatted(id))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.transaction.occurredOn")
                            .value(today().toString()))
                    .andExpect(jsonPath("$.data.transaction.title").value("스타벅스"))
                    .andExpect(jsonPath("$.data.transaction.amount").value(4500));
        }

        @Test
        @DisplayName("원본 날짜를 그대로 쓸 수도 있다")
        void keepsOriginalDate() throws Exception {
            LocalDate origin = today().minusDays(5);
            long id = LedgerFixture.transactionId(LedgerFixture.createTransaction(
                    mockMvc, authHeader, """
                            {"type": "EXPENSE", "amount": 4500, "assetId": %d,
                             "occurredOn": "%s"}
                            """.formatted(checking, origin)));

            mockMvc.perform(post("/api/ledger/transactions/%d/duplicate".formatted(id))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"useToday\": false}"))
                    .andExpect(jsonPath("$.data.transaction.occurredOn")
                            .value(origin.toString()));
        }

        @Test
        @DisplayName("복사본은 환불 연결을 물려받지 않는다 — 상쇄가 두 번 세어지면 안 된다")
        void doesNotCopyRefundLink() throws Exception {
            long id = LedgerFixture.transactionId(LedgerFixture.createTransaction(
                    mockMvc, authHeader, """
                            {"type": "EXPENSE", "amount": 30000, "assetId": %d,
                             "occurredOn": "%s"}
                            """.formatted(checking, today())));
            String refund = mockMvc.perform(
                            post("/api/ledger/transactions/%d/refund".formatted(id))
                                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"amount\": 10000}"))
                    .andReturn().getResponse().getContentAsString();
            long refundId = ((Number) JsonPath.read(refund, "$.data.refund.id")).longValue();

            mockMvc.perform(post("/api/ledger/transactions/%d/duplicate".formatted(refundId))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.transaction.refundOfId").doesNotExist());
        }
    }

    @Nested
    @DisplayName("다건 입력")
    class BulkCreate {

        @Test
        @DisplayName("여러 줄을 한 번에 적는다")
        void savesAll() throws Exception {
            mockMvc.perform(post("/api/ledger/transactions/bulk-create")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"transactions": [
                                      {"type": "EXPENSE", "amount": 4500, "assetId": %d,
                                       "occurredOn": "%s"},
                                      {"type": "EXPENSE", "amount": 12000, "assetId": %d,
                                       "occurredOn": "%s"}
                                    ]}
                                    """.formatted(checking, today(), checking, today())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.created", hasSize(2)))
                    .andExpect(jsonPath("$.data.scheduledCount").value(0));

            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.monthTotals.expense").value(16500));
        }

        @Test
        @DisplayName("한 줄이 거부되면 전부 롤백된다 — 일부만 들어가면 원장이 어긋난다")
        void rollsBackEverythingOnFailure() throws Exception {
            mockMvc.perform(post("/api/ledger/transactions/bulk-create")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"transactions": [
                                      {"type": "EXPENSE", "amount": 4500, "assetId": %d,
                                       "occurredOn": "%s"},
                                      {"type": "EXPENSE", "amount": 9999, "assetId": 999999,
                                       "occurredOn": "%s"}
                                    ]}
                                    """.formatted(checking, today(), today())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-001"));

            // 앞줄도 들어가지 않았다.
            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.monthTotals.expense").value(0))
                    .andExpect(jsonPath("$.data.groups").isEmpty());
        }

        @Test
        @DisplayName("미래 날짜가 섞이면 몇 건이 예정으로 갔는지 알려준다")
        void reportsScheduledCount() throws Exception {
            mockMvc.perform(post("/api/ledger/transactions/bulk-create")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"transactions": [
                                      {"type": "EXPENSE", "amount": 4500, "assetId": %d,
                                       "occurredOn": "%s"},
                                      {"type": "EXPENSE", "amount": 17000, "assetId": %d,
                                       "occurredOn": "%s"}
                                    ]}
                                    """.formatted(checking, today(), checking, today().plusDays(7))))
                    .andExpect(jsonPath("$.data.scheduledCount").value(1));
        }
    }

    @Nested
    @DisplayName("영수증")
    class Receipts {

        @Test
        @DisplayName("업로드 URL은 일상기록과 같은 버킷의 다른 prefix를 가리킨다")
        void issuesUploadUrl() throws Exception {
            mockMvc.perform(post("/api/ledger/receipts/upload-url")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contentType\": \"image/jpeg\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.objectKey")
                            .value(org.hamcrest.Matchers.startsWith("ledger/receipts/")))
                    .andExpect(jsonPath("$.data.uploadUrl").isNotEmpty());
        }

        @Test
        @DisplayName("거래에 붙이고 목록으로 돌려받는다")
        void attachesAndLists() throws Exception {
            long id = newTransaction();

            mockMvc.perform(post("/api/ledger/transactions/%d/receipts".formatted(id))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"objectKey": "ledger/receipts/1/a.jpg",
                                     "contentType": "image/jpeg", "byteSize": 1024}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.url")
                            .value(org.hamcrest.Matchers.endsWith("ledger/receipts/1/a.jpg")));

            mockMvc.perform(get("/api/ledger/transactions/%d/receipts".formatted(id))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data", hasSize(1)));
        }

        @Test
        @DisplayName("거래를 삭제해도 첨부 행은 남는다 — 되돌릴 수 있어야 한다")
        void keepsReceiptWhenTransactionDeleted() throws Exception {
            long id = newTransaction();
            mockMvc.perform(post("/api/ledger/transactions/%d/receipts".formatted(id))
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"objectKey\": \"ledger/receipts/1/b.jpg\"}"));

            mockMvc.perform(delete("/api/ledger/transactions/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            // 소프트 삭제라 거래도 첨부도 사라지지 않는다. 오브젝트 회수는 보존 배치의 몫이다.
            assertThat(receiptRepository.findAll()).hasSize(1);
        }
    }

    // --- 준비 ---

    private LocalDate today() {
        return LocalDate.now(TEST_ZONE);
    }

    private long newTransaction() throws Exception {
        return LedgerFixture.transactionId(LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": 4500, "assetId": %d, "occurredOn": "%s"}
                """.formatted(checking, today())));
    }

    private long createTemplate(String name, long amount) throws Exception {
        String body = mockMvc.perform(post("/api/ledger/templates")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "txType": "EXPENSE", "amount": %d,
                                 "assetId": %d, "categoryId": %d, "title": "%s"}
                                """.formatted(name, amount, checking, food, name)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }
}
