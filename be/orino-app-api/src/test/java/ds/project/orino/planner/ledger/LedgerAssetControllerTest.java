package ds.project.orino.planner.ledger;

import ds.project.orino.domain.member.repository.MemberRepository;
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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 자산과 잔액(#1259).
 *
 * <p><b>잔액은 저장되지 않는다</b>(D-8). 그래서 이 테스트가 확인하는 것은 값이 맞느냐가 아니라
 * <b>원장에서 같은 값이 다시 나오느냐</b>다. 저장된 컬럼이라면 확인할 일도 없었을 것이고,
 * 바로 그 점이 이 설계를 고른 이유다.
 */
class LedgerAssetControllerTest extends ApiTestSupport {

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

    @Nested
    @DisplayName("잔액 파생")
    class DerivedBalance {

        @Test
        @DisplayName("수입은 더하고 지출은 빼고 이체는 양쪽을 함께 움직인다")
        void derivesFromLedger() throws Exception {
            long checking = asset("급여통장", "CHECKING");
            long savings = asset("비상금", "SAVINGS");

            income(checking, 3000000);
            expense(checking, 250000);
            transfer(checking, savings, 500000);

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    // 3,000,000 − 250,000 − 500,000
                    .andExpect(jsonPath("$.data.groups[0].assets[?(@.name=='급여통장')].balance")
                            .value(2250000))
                    .andExpect(jsonPath("$.data.groups[0].assets[?(@.name=='비상금')].balance")
                            .value(500000))
                    .andExpect(jsonPath("$.data.totalAssets").value(2750000));
        }

        @Test
        @DisplayName("예정은 잔액을 바꾸지 않는다")
        void scheduledDoesNotMoveBalance() throws Exception {
            long checking = asset("급여통장", "CHECKING");
            income(checking, 1000000);
            // 미래 날짜 → 예정으로 저장된다.
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 300000, "assetId": %d, "occurredOn": "%s"}
                    """.formatted(checking, LocalDate.now(TEST_ZONE).plusDays(7)));

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.totalAssets").value(1000000));
        }

        @Test
        @DisplayName("거래가 없는 자산의 잔액은 0이다 — 「모른다」가 아니다")
        void zeroBalanceIsNotNull() throws Exception {
            asset("급여통장", "CHECKING");

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.groups[0].assets[0].balance").value(0));
        }
    }

    @Nested
    @DisplayName("체크카드")
    class DebitCard {

        @Test
        @DisplayName("연결 계좌가 없으면 만들 수 없다 — 유령 자산이 된다")
        void requiresLinkedAccount() throws Exception {
            mockMvc.perform(post("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"체크카드\", \"type\": \"DEBIT_CARD\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-019"));
        }

        @Test
        @DisplayName("카드로 쓴 돈은 연결 계좌에서 빠지고, 카드 자신은 잔액을 갖지 않는다")
        void spendingIsChargedToLinkedAccount() throws Exception {
            long checking = asset("급여통장", "CHECKING");
            long card = LedgerFixture.createAsset(mockMvc, authHeader, "체크카드", "DEBIT_CARD", checking);

            income(checking, 1000000);
            // 거래는 카드에 붙는다 — 그래야 "이 카드로 얼마 썼나"가 나온다.
            expense(card, 30000);

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.groups[0].assets[?(@.name=='급여통장')].balance")
                            .value(970000))
                    // 같은 돈이 두 자산에 잡히면 총자산이 부풀려진다. 필터 경로는 리스트로
                    // 오므로 「값이 null인 원소 하나」로 단언한다 — 빈 리스트가 아니다.
                    .andExpect(jsonPath("$.data.groups[0].assets[?(@.name=='체크카드')].balance")
                            .value(contains(nullValue())))
                    .andExpect(jsonPath("$.data.totalAssets").value(970000));
        }

        @Test
        @DisplayName("연결 계좌 화면의 잔액이 자산 목록의 잔액과 어긋나지 않는다")
        void linkedAccountDetailMatchesListBalance() throws Exception {
            long checking = asset("급여통장", "CHECKING");
            long card = LedgerFixture.createAsset(mockMvc, authHeader, "체크카드", "DEBIT_CARD", checking);

            income(checking, 1000000);
            expense(card, 12000);

            // 카드 지출은 카드에 붙어 있지만 돈은 이 계좌에서 빠졌다. 계좌 화면이 그 줄을
            // 빼고 그리면 마지막 잔액이 970,000이 아니라 1,000,000으로 남아 자산 목록과
            // 어긋난다 — 저장된 잔액을 두지 않기로 한 이유가 바로 그 어긋남이다.
            mockMvc.perform(get("/api/ledger/assets/%d/transactions".formatted(checking))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items", hasSize(2)))
                    .andExpect(jsonPath("$.data.items[0].runningBalance").value(988000));

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.groups[0].assets[?(@.name=='급여통장')].balance")
                            .value(988000));
        }

        @Test
        @DisplayName("연결 계좌의 카테고리 분포에 카드로 쓴 돈도 함께 잡힌다")
        void linkedAccountShareIncludesCardSpending() throws Exception {
            long checking = asset("급여통장", "CHECKING");
            long card = LedgerFixture.createAsset(mockMvc, authHeader, "체크카드", "DEBIT_CARD", checking);
            long food = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "식비");

            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 12000, "assetId": %d,
                     "categoryId": %d, "occurredOn": "%s"}
                    """.formatted(card, food, LocalDate.now(TEST_ZONE)));

            mockMvc.perform(get("/api/ledger/assets/" + checking)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("range", "MONTH"))
                    .andExpect(jsonPath("$.data.categoryShare", hasSize(1)))
                    .andExpect(jsonPath("$.data.categoryShare[0].amount").value(12000));
        }
    }

    @Nested
    @DisplayName("신용카드")
    class CreditCard {

        @Test
        @DisplayName("사용액은 잔액이 아니라 부채다 — 총자산에 섞이지 않는다")
        void usageBecomesLiability() throws Exception {
            long checking = asset("급여통장", "CHECKING");
            long card = asset("신한 Deep Dream", "CREDIT_CARD");

            income(checking, 1000000);
            expense(card, 180000);

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.totalAssets").value(1000000))
                    .andExpect(jsonPath("$.data.liabilities").value(180000))
                    .andExpect(jsonPath("$.data.netWorth").value(820000))
                    .andExpect(jsonPath("$.data.groups[0].assets[?(@.name=='신한 Deep Dream')].unpaidAmount")
                            .value(180000));
        }

        @Test
        @DisplayName("그룹 합계는 카드 빚을 빼고 센다 — 다 더하면 총자산과 맞아야 한다")
        void groupSubtotalSubtractsCardDebt() throws Exception {
            long checking = asset("급여통장", "CHECKING");
            long card = asset("신한 Deep Dream", "CREDIT_CARD");

            income(checking, 1000000);
            expense(card, 180000);

            // 빚을 더하면 그룹 합계가 1,180,000으로 나와 「이만큼 있다」로 읽힌다.
            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.groups[0].subtotal").value(820000))
                    .andExpect(jsonPath("$.data.netWorth").value(820000));
        }

        @Test
        @DisplayName("카드로 들어온 이체는 대금 납부다 — 빚이 그만큼 준다")
        void paymentReducesLiability() throws Exception {
            long checking = asset("급여통장", "CHECKING");
            long card = asset("신한 Deep Dream", "CREDIT_CARD");

            income(checking, 1000000);
            expense(card, 180000);
            transfer(checking, card, 180000);

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.liabilities").value(0))
                    .andExpect(jsonPath("$.data.totalAssets").value(820000));
        }
    }

    @Nested
    @DisplayName("상세")
    class Detail {

        @Test
        @DisplayName("내역 줄마다 그 시점의 잔액이 붙는다")
        void runningBalance() throws Exception {
            long checking = asset("급여통장", "CHECKING");
            income(checking, 1000000);
            expense(checking, 30000);
            expense(checking, 20000);

            mockMvc.perform(get("/api/ledger/assets/%d/transactions".formatted(checking))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items", hasSize(3)))
                    // 최신이 위다. 950,000 → 970,000 → 1,000,000
                    .andExpect(jsonPath("$.data.items[0].runningBalance").value(950000))
                    .andExpect(jsonPath("$.data.items[2].runningBalance").value(1000000));
        }

        @Test
        @DisplayName("카테고리 분포에 미분류도 한 칸을 차지한다 — 안 보이면 정리하지 않는다")
        void categoryShareIncludesUncategorized() throws Exception {
            long checking = asset("급여통장", "CHECKING");
            long food = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "식비");

            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "EXPENSE", "amount": 30000, "assetId": %d,
                     "categoryId": %d, "occurredOn": "%s"}
                    """.formatted(checking, food, LocalDate.now(TEST_ZONE)));
            expense(checking, 5000);

            mockMvc.perform(get("/api/ledger/assets/" + checking)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("range", "MONTH"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.categoryShare", hasSize(2)))
                    .andExpect(jsonPath("$.data.categoryShare[0].amount").value(30000))
                    .andExpect(jsonPath("$.data.categoryShare[1].categoryId").doesNotExist());
        }
    }

    @Nested
    @DisplayName("그룹과 숨김")
    class GroupsAndHidden {

        @Test
        @DisplayName("그룹 없는 자산은 「그 외」로 묶인다")
        void ungroupedFallsBackToEtc() throws Exception {
            asset("현금", "CASH");

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.groups[0].name").value("그 외"))
                    .andExpect(jsonPath("$.data.groups[0].id").doesNotExist());
        }

        @Test
        @DisplayName("숨긴 자산은 목록 본문에서 빠지되 사라지지 않는다")
        void hiddenAssetsAreKept() throws Exception {
            long card = asset("해지한 카드", "CREDIT_CARD");
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .patch("/api/ledger/assets/" + card)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"hidden\": true, \"closedReason\": \"CLOSED\"}"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.groups").isEmpty())
                    .andExpect(jsonPath("$.data.hidden", hasSize(1)))
                    .andExpect(jsonPath("$.data.hidden[0].closedReason").value("CLOSED"));
        }

        @Test
        @DisplayName("그룹을 만들면 그 그룹으로 자산을 묶을 수 있다")
        void groupsAssets() throws Exception {
            String body = mockMvc.perform(post("/api/ledger/asset-groups")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"국민은행\", \"kind\": \"BANK\"}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            long groupId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data[0].id")).longValue();

            mockMvc.perform(post("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "급여통장", "type": "CHECKING", "groupId": %d}
                                    """.formatted(groupId)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.groups[0].name").value("국민은행"))
                    .andExpect(jsonPath("$.data.groups[0].assets", hasSize(1)));
        }
    }

    /**
     * 삭제(#1312).
     *
     * <p>확인하는 것은 「지워지느냐」가 아니라 <b>지워지면 안 되는 것이 남느냐</b>다.
     * 거래 한 줄이 이 자산을 가리키는 순간 삭제는 해지로 바뀌어야 한다.
     */
    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("아무것도 붙지 않은 자산은 지워진다 — 잘못 만든 줄을 되돌릴 길이 있어야 한다")
        void deletesUntouchedAsset() throws Exception {
            long id = asset("잘못 만든 통장", "CHECKING");

            mockMvc.perform(delete("/api/ledger/assets/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.groups").isEmpty())
                    .andExpect(jsonPath("$.data.hidden").isEmpty());
        }

        @Test
        @DisplayName("거래가 붙은 자산은 거부한다 — 그 내역이 갈 곳을 잃는다")
        void rejectsAssetWithTransaction() throws Exception {
            long id = asset("급여통장", "CHECKING");
            income(id, 1000000);

            mockMvc.perform(delete("/api/ledger/assets/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-034"));
        }

        @Test
        @DisplayName("지운 거래도 센다 — 소프트 삭제라 그 행은 아직 이 자산을 가리킨다")
        void countsSoftDeletedTransaction() throws Exception {
            long id = asset("급여통장", "CHECKING");
            long transactionId = LedgerFixture.transactionId(
                    LedgerFixture.createTransaction(mockMvc, authHeader, """
                            {"type": "INCOME", "amount": 1000, "assetId": %d, "occurredOn": "%s"}
                            """.formatted(id, LocalDate.now(TEST_ZONE))));
            mockMvc.perform(delete("/api/ledger/transactions/" + transactionId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/ledger/assets/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-034"));
        }

        @Test
        @DisplayName("체크카드가 물고 있는 계좌는 거부한다")
        void rejectsLinkedAccount() throws Exception {
            long checking = asset("급여통장", "CHECKING");
            LedgerFixture.createAsset(mockMvc, authHeader, "체크카드", "DEBIT_CARD", checking);

            mockMvc.perform(delete("/api/ledger/assets/" + checking)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-034"));
        }

        @Test
        @DisplayName("기본 자산으로 걸려 있어도 지워지고, 설정만 풀린다")
        void clearsDefaultAssetSetting() throws Exception {
            long id = asset("잘못 만든 통장", "CHECKING");
            mockMvc.perform(patch("/api/ledger/settings")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"defaultAssetId": %d}
                                    """.formatted(id)))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/ledger/assets/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            // 없는 자산을 가리키는 설정이 남으면 입력 모달이 빈 자리를 고른다.
            mockMvc.perform(get("/api/ledger/settings")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.defaultAssetId").value(nullValue()));
        }

        @Test
        @DisplayName("남의 자산은 없는 것으로 본다")
        void rejectsOthersAsset() throws Exception {
            mockMvc.perform(delete("/api/ledger/assets/999999")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-001"));
        }
    }

    // --- 준비 ---

    private long asset(String name, String type) throws Exception {
        return LedgerFixture.createAsset(mockMvc, authHeader, name, type);
    }

    private void income(long assetId, long amount) throws Exception {
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "INCOME", "amount": %d, "assetId": %d, "occurredOn": "%s"}
                """.formatted(amount, assetId, LocalDate.now(TEST_ZONE)));
    }

    private void expense(long assetId, long amount) throws Exception {
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": %d, "assetId": %d, "occurredOn": "%s"}
                """.formatted(amount, assetId, LocalDate.now(TEST_ZONE)));
    }

    private void transfer(long from, long to, long amount) throws Exception {
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "TRANSFER", "amount": %d, "assetId": %d,
                 "counterAssetId": %d, "occurredOn": "%s"}
                """.formatted(amount, from, to, LocalDate.now(TEST_ZONE)));
    }
}
