package ds.project.orino.planner.travel.expense;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
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
 *
 * <p><b>원본은 손으로 심는다.</b> 예전에는 가계부 API로 거래를 만들었는데 그 API가
 * 없어졌다(#1409). 테이블은 3단계까지 남아 있으므로 그 자리에 직접 넣는다 — 이 테스트가
 * 보는 것은 애초에 API가 아니라 <b>테이블에 있는 행</b>이다.
 *
 * <p>심은 행은 {@link #clearLedger()}가 스스로 지운다. {@code DbCleaner}는 더 이상
 * 가계부를 모른다(#1409) — 쓰는 곳이 여기뿐이니 치우는 곳도 여기다.
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
    private long memberId;
    private long checking;
    private long osaka;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        clearLedger();
        Member member = memberRepository.save(MemberFixture.create());
        memberId = member.getId();
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        checking = insertAsset("국민 체크");
        osaka = TravelCityFixture.createCity(mockMvc, authHeader, "오사카", "Asia/Tokyo", "JPY");
    }

    @Test
    @DisplayName("이관 전후로 여행의 확정 지출 합계가 같다 — 다르면 배포를 멈춘다")
    void keepsConfirmedTotal() throws Exception {
        long tripId = createTrip();
        ledgerExpense(tripId, 640000, "항공권", "2025-11-30", "CONFIRMED");
        ledgerExpense(tripId, 32000, "이자카야", "2026-01-15", "CONFIRMED");
        // 예정은 확정 합계에 들어가지 않지만 함께 옮겨져야 한다 — 게이지 2층이다.
        ledgerExpense(tripId, 80000, "숙소 잔금", "2026-01-17", "SCHEDULED");

        runMigration();

        expenses(tripId)
                .andExpect(jsonPath("$.data.totals.spent").value(672000))
                .andExpect(jsonPath("$.data.totals.scheduled").value(80000));
    }

    @Test
    @DisplayName("경비가 아니었던 것은 오지 않는다 — 이체·수입·지운 거래")
    void copiesOnlyWhatTheScreenCounted() throws Exception {
        long tripId = createTrip();
        ledgerExpense(tripId, 32000, "이자카야", "2026-01-15", "CONFIRMED");
        // 카드 대금 납부는 이체라 애초에 여행 경비가 아니었다(§4.2).
        long savings = insertAsset("비상금");
        insertTransaction(tripId, "TRANSFER", "CONFIRMED", 500000, "카드 대금",
                "2026-01-15", savings, null, false);
        insertTransaction(tripId, "INCOME", "CONFIRMED", 100000, "환급",
                "2026-01-15", null, null, false);
        // 지운 거래도 마찬가지다 — 화면에 없던 것이 이관으로 되살아나면 안 된다.
        insertTransaction(tripId, "EXPENSE", "CONFIRMED", 9900, "취소한 예약",
                "2026-01-15", null, null, true);

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
        ledgerExpense(tripId, 32000, "이자카야", "2026-01-15", "CONFIRMED");

        runMigration();

        expenses(tripId).andExpect(
                jsonPath("$.data.groups[1].rows[0].paymentMethod").value("국민 체크"));
    }

    @Test
    @DisplayName("이름이 확실한 분류만 옮기고 나머지는 「정리할 내역」이 된다")
    void mapsOnlyUnambiguousCategories() throws Exception {
        long tripId = createTrip();
        long food = insertCategory("식비");
        long culture = insertCategory("문화");
        insertTransaction(tripId, "EXPENSE", "CONFIRMED", 32000, "이자카야",
                "2026-01-15", null, food, false);
        insertTransaction(tripId, "EXPENSE", "CONFIRMED", 18000, "전시",
                "2026-01-15", null, culture, false);

        runMigration();

        // 「문화」는 여섯 프리셋에 딱 떨어지지 않는다. 억지로 넣지 않고 화면이 잡게 둔다.
        expenses(tripId)
                .andExpect(jsonPath("$.data.groups[1].rows[0].category").value("FOOD"))
                .andExpect(jsonPath("$.data.groups[1].rows[1].category").value(nullValue()))
                .andExpect(jsonPath("$.data.unsortedCount").value(1));
    }

    @Test
    @DisplayName("굳은 환율이 그대로 따라온다 — 이관이 총액을 다시 계산하지 않는다")
    void carriesFrozenRate() throws Exception {
        long tripId = createTrip();
        insertTransaction(tripId, "EXPENSE", "CONFIRMED", 10800, "이자카야",
                "2026-01-15", null, null, false);
        jdbcTemplate.update("""
                UPDATE ledger_transaction
                   SET fx_currency = 'JPY', fx_amount = 1200.00, fx_rate = 9.000000
                 WHERE trip_id = ?
                """, tripId);

        runMigration();

        expenses(tripId)
                .andExpect(jsonPath("$.data.totals.spent").value(10800))
                .andExpect(jsonPath("$.data.groups[1].rows[0].fx.currency").value("JPY"))
                .andExpect(jsonPath("$.data.groups[1].rows[0].fx.rate").value(9.0));
    }

    // ---------------- 이관 ----------------

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

    // ---------------- 원본 심기 ----------------

    /**
     * 심어 둔 원장을 지운다. 순서는 참조를 거슬러 올라간다 — 거래가 자산·분류를 가리킨다.
     *
     * <p>{@code trip_expense}까지 지우지 않는다. 그건 {@code DbCleaner}가 여행 테이블과
     * 함께 치우고, 여기서 또 지우면 두 곳이 같은 것을 책임지게 된다.
     */
    private void clearLedger() {
        jdbcTemplate.execute("DELETE FROM ledger_transaction");
        jdbcTemplate.execute("DELETE FROM ledger_category");
        jdbcTemplate.execute("DELETE FROM ledger_asset");
    }

    private long insertAsset(String name) {
        jdbcTemplate.update("""
                INSERT INTO ledger_asset
                    (member_id, name, type, display_order, hidden, created_at, updated_at)
                VALUES (?, ?, 'CHECKING', 0, 0, NOW(6), NOW(6))
                """, memberId, name);
        return lastInsertId();
    }

    private long insertCategory(String name) {
        jdbcTemplate.update("""
                INSERT INTO ledger_category
                    (member_id, flow, name, display_order, archived, created_at, updated_at)
                VALUES (?, 'EXPENSE', ?, 0, 0, NOW(6), NOW(6))
                """, memberId, name);
        return lastInsertId();
    }

    private void ledgerExpense(long tripId, long amount, String title, String date,
                               String status) {
        insertTransaction(tripId, "EXPENSE", status, amount, title, date, null, null, false);
    }

    /** {@code assetId}가 {@code null}이면 기본 자산에 붙는다. */
    private void insertTransaction(long tripId, String type, String status, long amount,
                                   String title, String occurredOn, Long assetId,
                                   Long categoryId, boolean deleted) {
        jdbcTemplate.update("""
                INSERT INTO ledger_transaction
                    (member_id, type, status, occurred_on, amount, asset_id, category_id,
                     title, source, estimated, trip_id, created_at, updated_at, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'MANUAL', 0, ?, NOW(6), NOW(6), ?)
                """,
                memberId, type, status, occurredOn, amount,
                assetId == null ? checking : assetId, categoryId, title, tripId,
                deleted ? "2026-01-16 00:00:00" : null);
    }

    private long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    // ---------------- 여행 ----------------

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
