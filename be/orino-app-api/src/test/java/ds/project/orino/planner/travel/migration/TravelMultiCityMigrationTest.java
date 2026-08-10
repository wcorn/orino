package ds.project.orino.planner.travel.migration;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v2.1 마이그레이션(053)이 <b>이미 데이터가 있는 DB</b>에서 무엇을 하는지 고정한다.
 *
 * <p>보통의 테스트는 빈 DB에 changelog를 처음부터 적용하므로 백필 구문이 한 행도 건드리지 않아
 * "돌긴 했다"는 것 말고는 아무것도 증명하지 못한다. 여기서는 태그 지점까지 <b>되돌린 뒤</b>
 * v2.0 형태의 여행을 심고 다시 적용한다 — 롤백문과 백필문이 둘 다 실제로 검증된다.
 *
 * <p>백필 없이 컬럼만 지우면 기존 여행이 전부 타임존을 잃고 상태 판정이 죽는다. 실사용 데이터가
 * 아직 없어 손실 위험은 낮지만, 그 순서를 지켰는지는 코드가 아니라 DB에 물어봐야 안다.
 *
 * <p>공용 TestContainer를 쓰지 않고 <b>이 클래스 전용 컨테이너</b>를 띄운다 — 스키마를 통째로
 * 되감았다 다시 감는 시험이라, 같은 DB를 쓰는 다른 테스트와 섞이면 안 된다.
 */
class TravelMultiCityMigrationTest {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";
    private static final String TAG = "pre-travel-multi-city";

    @SuppressWarnings("resource")
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.4");

    private static Connection connection;

    @BeforeAll
    static void startDatabase() throws Exception {
        MYSQL.start();
        connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        if (connection != null) {
            connection.close();
        }
        MYSQL.stop();
    }

    @Test
    @DisplayName("v2.0 여행이 담긴 DB에 적용하면 날짜마다 기준 도시가 붙고 목적지 컬럼이 사라진다")
    void backfillsDaysAndDropsDestinationColumns() throws Exception {
        runLiquibase(liquibase -> liquibase.update(new Contexts(), new LabelExpression()));

        // 태그 지점까지 되돌리면 v2.0 스키마다 — 되돌리기가 정말 되는지부터 확인한다.
        runLiquibase(liquibase -> liquibase.rollback(TAG, new Contexts(), new LabelExpression()));
        assertThat(columnExists("trip", "timezone")).isTrue();
        assertThat(tableExists("trip_day")).isFalse();

        seedV20Trips();

        runLiquibase(liquibase -> liquibase.update(new Contexts(), new LabelExpression()));

        // 1) 목적지 컬럼은 사라졌다. 남겨두면 trip.timezone을 읽는 코드가 살아남는다.
        assertThat(columnExists("trip", "destination_name")).isFalse();
        assertThat(columnExists("trip", "destination_place_id")).isFalse();
        assertThat(columnExists("trip", "timezone")).isFalse();
        assertThat(columnExists("trip", "currency")).isFalse();
        assertThat(columnExists("trip", "lat")).isFalse();
        assertThat(columnExists("trip", "lng")).isFalse();

        // 2) 검색으로 고른 목적지 장소는 CITY로 승격되고 타임존·통화를 넘겨받았다.
        assertThat(queryOne("SELECT place_kind FROM travel_place WHERE id = 1")).isEqualTo("CITY");
        assertThat(queryOne("SELECT timezone FROM travel_place WHERE id = 1"))
                .isEqualTo("Asia/Tokyo");
        assertThat(queryOne("SELECT currency FROM travel_place WHERE id = 1")).isEqualTo("JPY");
        assertThat(queryOne("SELECT city_name FROM travel_place WHERE id = 1")).isEqualTo("오사카");
        // 도시 식별자는 자기 구글 id다 — 일정 장소의 city_place_ref와 같은 축에서 비교된다.
        assertThat(queryOne("SELECT city_place_ref FROM travel_place WHERE id = 1"))
                .isEqualTo("ChIJ_osaka");

        // 3) 하루도 비지 않는다. 빈 날짜는 타임존이 없는 날이고 그 순간 판정이 전부 죽는다.
        assertThat(queryOne("SELECT COUNT(*) FROM trip_day WHERE trip_id = 1")).isEqualTo("4");
        assertThat(queryList("SELECT day_date FROM trip_day WHERE trip_id = 1 ORDER BY day_date"))
                .containsExactly("2026-10-24", "2026-10-25", "2026-10-26", "2026-10-27");
        // 기존 여행은 단일 도시라 전 날짜가 같은 도시를 가리킨다.
        assertThat(queryList(
                "SELECT DISTINCT base_place_id FROM trip_day WHERE trip_id = 1"))
                .containsExactly("1");

        // 4) 목적지를 직접 입력한 여행(장소 참조 없음)도 도시 장소를 새로 만들어 붙였다.
        assertThat(queryOne("SELECT COUNT(*) FROM trip_day WHERE trip_id = 2")).isEqualTo("2");
        assertThat(queryOne("""
                SELECT p.name FROM trip_day d JOIN travel_place p ON p.id = d.base_place_id
                 WHERE d.trip_id = 2 LIMIT 1""")).isEqualTo("교토");
        assertThat(queryOne("""
                SELECT p.timezone FROM trip_day d JOIN travel_place p ON p.id = d.base_place_id
                 WHERE d.trip_id = 2 LIMIT 1""")).isEqualTo("Asia/Tokyo");
        assertThat(queryOne("""
                SELECT p.place_kind FROM trip_day d JOIN travel_place p ON p.id = d.base_place_id
                 WHERE d.trip_id = 2 LIMIT 1""")).isEqualTo("CITY");
        // 좌표까지 옮겨야 그 도시 날씨가 나온다.
        assertThat(queryOne("""
                SELECT p.lat FROM trip_day d JOIN travel_place p ON p.id = d.base_place_id
                 WHERE d.trip_id = 2 LIMIT 1""")).isEqualTo("35.0116000");

        // 5) 숙소 테이블은 비어 있지만 자리는 마련돼 있다(3단계에서 채운다).
        assertThat(tableExists("trip_stay")).isTrue();
    }

    /** v2.0 형태의 여행 두 건 — 하나는 검색으로 고른 목적지, 하나는 직접 입력. */
    private void seedV20Trips() throws SQLException {
        execute("""
                INSERT INTO travel_place
                    (id, member_id, google_place_id, name, lat, lng, manual_entry,
                     created_at, updated_at)
                VALUES (1, 1, 'ChIJ_osaka', '오사카', 34.6937249, 135.5022535, b'0',
                        NOW(6), NOW(6))""");
        execute("""
                INSERT INTO trip
                    (id, member_id, title, destination_name, destination_place_id,
                     start_date, end_date, timezone, currency, lat, lng,
                     default_notify_minutes, morning_summary_enabled, created_at, updated_at)
                VALUES (1, 1, '오사카 3박4일', '오사카', 1,
                        '2026-10-24', '2026-10-27', 'Asia/Tokyo', 'JPY',
                        34.6937249, 135.5022535, 15, b'0', NOW(6), NOW(6))""");
        execute("""
                INSERT INTO trip
                    (id, member_id, title, destination_name, destination_place_id,
                     start_date, end_date, timezone, currency, lat, lng,
                     default_notify_minutes, morning_summary_enabled, created_at, updated_at)
                VALUES (2, 1, '교토 1박2일', '교토', NULL,
                        '2026-11-01', '2026-11-02', 'Asia/Tokyo', 'JPY',
                        35.0116, 135.7681, 15, b'0', NOW(6), NOW(6))""");
    }

    // ---------------- helpers ----------------

    private interface LiquibaseAction {
        void run(Liquibase liquibase) throws Exception;
    }

    /**
     * {@code Liquibase#close()}가 넘겨받은 커넥션까지 닫으므로 실행마다 따로 연다 —
     * 조회용 커넥션을 물려주면 두 번째 호출이 닫힌 커넥션을 쓴다.
     */
    private void runLiquibase(LiquibaseAction action) throws Exception {
        try (Connection own = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(own));
            try (Liquibase liquibase =
                         new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
                action.run(liquibase);
            }
        }
    }

    private boolean tableExists(String table) throws SQLException {
        return queryOne("""
                SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema = DATABASE() AND table_name = '%s'"""
                .formatted(table)).equals("1");
    }

    private boolean columnExists(String table, String column) throws SQLException {
        return queryOne("""
                SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = '%s' AND column_name = '%s'"""
                .formatted(table, column)).equals("1");
    }

    private String queryOne(String sql) throws SQLException {
        List<String> rows = queryList(sql);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private List<String> queryList(String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            List<String> values = new ArrayList<>();
            while (rs.next()) {
                values.add(rs.getString(1));
            }
            return values;
        }
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
