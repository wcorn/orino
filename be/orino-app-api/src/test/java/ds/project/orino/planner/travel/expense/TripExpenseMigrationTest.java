package ds.project.orino.planner.travel.expense;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.planner.ledger.LedgerFixture;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.FixedClock;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.TravelCityFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 가계부 → 여행 장부 이관(경비 독립 §5.2 · 제거 계획 0단계).
 *
 * <p><b>배포를 멈추는 기준이 하나뿐이다</b> — 이관 전후로 각 여행의 확정 지출 합계가 같아야
 * 한다. 원본은 3단계(#1410)에서 테이블째 사라지므로, 이 단언이 틀린 채로 넘어가면 되돌릴
 * 것이 없다.
 *
 * <p><b>SQL을 베껴 쓰지 않는다.</b> 체인지셋 파일에서 실제로 도는 문장을 꺼내 실행한다 —
 * 테스트가 자기만의 INSERT를 들고 있으면 통과해도 운영에서 도는 것을 확인한 것이 아니다.
 * 테스트 DB는 Liquibase가 만들지만 그때 가계부가 비어 있어, 이관은 아무 행도 옮기지 않은
 * 상태로 이미 지나간다.
 */
@FixedClock
class TripExpenseMigrationTest extends ApiTestSupport {

    private static final String CHANGELOG =
            "db/changelog/changes/070-create-trip-expense.yaml";
    private static final String MIGRATION_ID = "070-migrate-ledger-trip-expenses";

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String authHeader;
    private long checking;
    private long osaka;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        checking = LedgerFixture.createAsset(mockMvc, authHeader, "국민 체크", "CHECKING");
        osaka = TravelCityFixture.createCity(mockMvc, authHeader, "오사카", "Asia/Tokyo", "JPY");
    }

    @Test
    @DisplayName("이관 전후로 여행의 확정 지출 합계가 같다 — 다르면 배포를 멈춘다")
    void keepsConfirmedTotal() throws Exception {
        long tripId = createTrip();
        ledgerExpense(tripId, 640000, "항공권", "2025-11-30");
        ledgerExpense(tripId, 32000, "이자카야", "2026-01-15");
        // 예정은 확정 합계에 들어가지 않지만 함께 옮겨져야 한다 — 게이지 2층이다.
        ledgerExpense(tripId, 80000, "숙소 잔금", "2026-01-17");

        runMigration();

        expenses(tripId)
                .andExpect(jsonPath("$.data.totals.spent").value(672000))
                .andExpect(jsonPath("$.data.totals.scheduled").value(80000));
    }

    @Test
    @DisplayName("경비가 아니었던 것은 오지 않는다 — 이체·수입·지운 거래")
    void copiesOnlyWhatTheScreenCounted() throws Exception {
        long tripId = createTrip();
        ledgerExpense(tripId, 32000, "이자카야", "2026-01-15");
        // 카드 대금 납부는 이체라 애초에 여행 경비가 아니었다(§4.2).
        long savings = LedgerFixture.createAsset(mockMvc, authHeader, "비상금", "SAVINGS");
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "TRANSFER", "amount": 500000, "occurredOn": "2026-01-15",
                 "assetId": %d, "counterAssetId": %d, "tripId": %d}
                """.formatted(checking, savings, tripId));
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "INCOME", "amount": 100000, "occurredOn": "2026-01-15",
                 "assetId": %d, "tripId": %d}
                """.formatted(checking, tripId));
        // 지운 거래도 마찬가지다 — 화면에 없던 것이 이관으로 되살아나면 안 된다.
        long deleted = LedgerFixture.transactionId(
                LedgerFixture.createTransaction(mockMvc, authHeader, """
                        {"type": "EXPENSE", "amount": 9900, "occurredOn": "2026-01-15",
                         "assetId": %d, "tripId": %d}
                        """.formatted(checking, tripId)));
        deleteTransaction(deleted);

        runMigration();

        expenses(tripId)
                .andExpect(jsonPath("$.data.totals.spent").value(32000))
                .andExpect(jsonPath("$.data.groups[1].rows[0].title").value("이자카야"));
        assertThat(countExpenses(tripId)).isEqualTo(1);
    }

    @Test
    @DisplayName("결제수단은 자산 이름을 문자열로 받아 온다 — 자산을 옮기지 않는다")
    void copiesAssetNameAsLabel() throws Exception {
        long tripId = createTrip();
        ledgerExpense(tripId, 32000, "이자카야", "2026-01-15");

        runMigration();

        expenses(tripId).andExpect(
                jsonPath("$.data.groups[1].rows[0].paymentMethod").value("국민 체크"));
    }

    @Test
    @DisplayName("이름이 확실한 분류만 옮기고 나머지는 「정리할 내역」이 된다")
    void mapsOnlyUnambiguousCategories() throws Exception {
        long tripId = createTrip();
        long food = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "식비");
        long culture = LedgerFixture.categoryIdByName(mockMvc, authHeader, "EXPENSE", "문화");
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": 32000, "occurredOn": "2026-01-15",
                 "assetId": %d, "title": "이자카야", "categoryId": %d, "tripId": %d}
                """.formatted(checking, food, tripId));
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": 18000, "occurredOn": "2026-01-15",
                 "assetId": %d, "title": "전시", "categoryId": %d, "tripId": %d}
                """.formatted(checking, culture, tripId));

        runMigration();

        // 「문화」는 여섯 프리셋에 딱 떨어지지 않는다. 억지로 넣지 않고 화면이 잡게 둔다.
        expenses(tripId)
                .andExpect(jsonPath("$.data.groups[1].rows[0].category").value("FOOD"))
                .andExpect(jsonPath("$.data.groups[1].rows[1].category")
                        .value(nullValue()))
                .andExpect(jsonPath("$.data.unsortedCount").value(1));
    }

    @Test
    @DisplayName("굳은 환율이 그대로 따라온다 — 이관이 총액을 다시 계산하지 않는다")
    void carriesFrozenRate() throws Exception {
        long tripId = createTrip();
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "occurredOn": "2026-01-15", "assetId": %d,
                 "title": "이자카야", "tripId": %d,
                 "fx": {"currency": "JPY", "amount": 1200.00, "rate": 9.0}}
                """.formatted(checking, tripId));

        runMigration();

        expenses(tripId)
                .andExpect(jsonPath("$.data.totals.spent").value(10800))
                .andExpect(jsonPath("$.data.groups[1].rows[0].fx.currency").value("JPY"))
                .andExpect(jsonPath("$.data.groups[1].rows[0].fx.rate").value(9.0));
    }

    // ---------------- helpers ----------------

    /**
     * 체인지셋 파일에서 이관 SQL을 꺼내 그대로 실행한다. <b>여기서 베껴 쓰면 검증이 아니다</b> —
     * 운영에서 도는 문장과 다른 문장을 통과시키게 된다.
     */
    private void runMigration() {
        jdbcTemplate.execute(migrationSql());
    }

    @SuppressWarnings("unchecked")
    private static String migrationSql() {
        Map<String, Object> root;
        try (InputStream in = new ClassPathResource(CHANGELOG).getInputStream()) {
            root = new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException("체인지셋을 읽지 못했다: " + CHANGELOG, e);
        }
        for (Object entry : (List<Object>) root.get("databaseChangeLog")) {
            Map<String, Object> changeSet =
                    (Map<String, Object>) ((Map<String, Object>) entry).get("changeSet");
            if (!MIGRATION_ID.equals(changeSet.get("id"))) {
                continue;
            }
            Map<String, Object> change =
                    (Map<String, Object>) ((List<Object>) changeSet.get("changes")).get(0);
            return (String) ((Map<String, Object>) change.get("sql")).get("sql");
        }
        throw new IllegalStateException("이관 체인지셋이 없다: " + MIGRATION_ID);
    }

    /** 오늘(1/15)이 2일차인 여행. */
    private long createTrip() throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "일본", "startDate": "2026-01-14",
                                 "endDate": "2026-01-16", %s}
                                """.formatted(TravelCityFixture.singleLeg(osaka, 3))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private void ledgerExpense(long tripId, long amount, String title, String date)
            throws Exception {
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": %d, "occurredOn": "%s",
                 "assetId": %d, "title": "%s", "tripId": %d}
                """.formatted(amount, date, checking, title, tripId));
    }

    private void deleteTransaction(long id) throws Exception {
        mockMvc.perform(delete("/api/ledger/transactions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }

    private ResultActions expenses(long tripId) throws Exception {
        return mockMvc.perform(get("/api/travel/trips/" + tripId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }

    private Integer countExpenses(long tripId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trip_expense WHERE trip_id = ?", Integer.class, tripId);
    }
}
