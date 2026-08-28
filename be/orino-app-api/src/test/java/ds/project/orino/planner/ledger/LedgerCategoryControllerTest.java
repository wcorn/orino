package ds.project.orino.planner.ledger;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.StubExternalsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 카테고리(#1259) — 프리셋 · 2단 제한 · 통합.
 *
 * <p>통합이 이 클래스에서 가장 중요하다. <b>거래가 하나도 사라지지 않아야</b> 하고,
 * 원본 카테고리도 지워지지 않고 보관돼야 한다 — 지우면 과거 통계에서 그 이름이 사라진다.
 */
@Import(StubExternalsConfig.class)
class LedgerCategoryControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    @Test
    @DisplayName("최초 진입에 기본 카테고리가 심긴다 — 회원 생성 시점이 아니다")
    void seedsPresetOnFirstAccess() throws Exception {
        mockMvc.perform(get("/api/ledger/categories")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("flow", "EXPENSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(13)))
                // 카드 이자·수수료가 갈 곳이 처음부터 있어야 v1.5에서 미분류로 쌓이지 않는다.
                .andExpect(jsonPath("$.data[?(@.name=='이자/수수료')]", hasSize(1)));
    }

    @Test
    @DisplayName("두 번 불러도 다시 심지 않는다")
    void seedsOnlyOnce() throws Exception {
        mockMvc.perform(get("/api/ledger/categories")
                .header(HttpHeaders.AUTHORIZATION, authHeader));

        mockMvc.perform(get("/api/ledger/categories")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("flow", "EXPENSE"))
                .andExpect(jsonPath("$.data", hasSize(13)));
    }

    @Test
    @DisplayName("하위 분류는 대분류 아래에만 붙는다 — 3단은 거부한다")
    void rejectsThirdLevel() throws Exception {
        long food = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "식비");
        long dining = createChild("외식", food);

        mockMvc.perform(post("/api/ledger/categories")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flow": "EXPENSE", "name": "점심", "parentId": %d}
                                """.formatted(dining)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LDG-ERR-015"));
    }

    @Test
    @DisplayName("자기 자신을 부모로 삼을 수 없다")
    void rejectsSelfParent() throws Exception {
        long food = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "식비");

        mockMvc.perform(patch("/api/ledger/categories/" + food)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\": %d}".formatted(food)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LDG-ERR-014"));
    }

    @Test
    @DisplayName("자기 하위를 부모로 삼을 수 없다 — 순환이다")
    void rejectsCycle() throws Exception {
        long food = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "식비");
        long dining = createChild("외식", food);

        mockMvc.perform(patch("/api/ledger/categories/" + food)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\": %d}".formatted(dining)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LDG-ERR-014"));
    }

    @Test
    @DisplayName("통합하면 내역이 따라오고 거래는 하나도 사라지지 않는다")
    void mergeMovesTransactions() throws Exception {
        long checking = LedgerFixture.createAsset(mockMvc, authHeader, "급여통장", "CHECKING");
        long food = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "식비");
        long cafe = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "카페/간식");

        String created = LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": 4500, "assetId": %d,
                 "categoryId": %d, "occurredOn": "%s"}
                """.formatted(checking, cafe, LocalDate.now(TEST_ZONE)));
        long txId = LedgerFixture.transactionId(created);

        mockMvc.perform(patch("/api/ledger/categories/%d/merge".formatted(cafe))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetCategoryId\": %d}".formatted(food)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.movedTransactions").value(1));

        // 거래는 그대로 있고 소속만 옮겨졌다.
        mockMvc.perform(get("/api/ledger/transactions/" + txId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(4500))
                .andExpect(jsonPath("$.data.categoryId").value((int) food));

        // 지출 합계는 통합 전후로 같다 — 옮겼을 뿐 없어진 돈이 아니다.
        mockMvc.perform(get("/api/ledger/transactions")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.monthTotals.expense").value(4500));
    }

    @Test
    @DisplayName("종류가 다른 카테고리끼리는 통합할 수 없다")
    void rejectsMergeAcrossFlows() throws Exception {
        long food = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "식비");
        long salary = LedgerFixture.categoryIdByName(mockMvc, authHeader, "INCOME", "급여");

        mockMvc.perform(patch("/api/ledger/categories/%d/merge".formatted(food))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetCategoryId\": %d}".formatted(salary)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LDG-ERR-005"));
    }

    @Test
    @DisplayName("삭제는 보관이다 — 붙어 있던 거래는 그대로 남는다")
    void deleteArchivesAndKeepsTransactions() throws Exception {
        long checking = LedgerFixture.createAsset(mockMvc, authHeader, "급여통장", "CHECKING");
        long cafe = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "카페/간식");
        long txId = LedgerFixture.transactionId(LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": 4500, "assetId": %d,
                 "categoryId": %d, "occurredOn": "%s"}
                """.formatted(checking, cafe, LocalDate.now(TEST_ZONE))));

        mockMvc.perform(delete("/api/ledger/categories/" + cafe)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/ledger/transactions/" + txId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryId").value((int) cafe))
                .andExpect(jsonPath("$.data.categoryName").value("카페/간식"));
    }

    private long createChild(String name, long parentId) throws Exception {
        String body = mockMvc.perform(post("/api/ledger/categories")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flow": "EXPENSE", "name": "%s", "parentId": %d}
                                """.formatted(name, parentId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }
}
