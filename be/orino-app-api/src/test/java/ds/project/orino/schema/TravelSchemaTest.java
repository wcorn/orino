package ds.project.orino.schema;

import ds.project.orino.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 여행 스키마(047)에서 Hibernate {@code validate}가 봐주지 않는 것들을 고정한다 —
 * <b>FK 삭제 규칙 · 인덱스 · 컬럼 타입</b>. validate는 컬럼의 존재와 타입 호환만 보므로
 * cascade가 빠져도, 인덱스가 없어도 통과한다.
 *
 * <p>도메인 모듈의 Repository 테스트는 {@code create-drop}으로 엔티티에서 스키마를 만들어
 * FK 자체가 없다. 그래서 cascade·SET NULL은 Liquibase 스키마가 적용된 여기서만 볼 수 있다.
 */
@IntegrationTest
class TravelSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("여행을 지우면 일정도 함께 지워진다(ON DELETE CASCADE)")
    @Transactional
    void deletingTripCascadesToActivities() {
        long memberId = insertMember("cascade-member");
        long tripId = insertTrip(memberId, "도쿄");
        jdbcTemplate.update("""
                INSERT INTO trip_activity (trip_id, title, activity_date, sort_order, start_time,
                                           notify_enabled, departure_notify_enabled,
                                           created_at, updated_at)
                VALUES (?, '센소지', '2026-10-24', 0, '10:30:00', b'0', b'0', NOW(6), NOW(6))
                """, tripId);

        jdbcTemplate.update("DELETE FROM trip WHERE id = ?", tripId);

        assertThat(countActivities(tripId)).isZero();
    }

    @Test
    @DisplayName("장소를 지우면 일정은 남고 참조만 끊긴다(ON DELETE SET NULL)")
    @Transactional
    void deletingPlaceNullsActivityReference() {
        long memberId = insertMember("setnull-member");
        long tripId = insertTrip(memberId, "도쿄");
        long placeId = insertPlace(memberId, "ChIJ_senso_ji", "센소지");
        jdbcTemplate.update("""
                INSERT INTO trip_activity (trip_id, title, activity_date, sort_order, place_id,
                                           notify_enabled, departure_notify_enabled,
                                           created_at, updated_at)
                VALUES (?, '센소지', '2026-10-24', 0, ?, b'0', b'0', NOW(6), NOW(6))
                """, tripId, placeId);

        jdbcTemplate.update("DELETE FROM travel_place WHERE id = ?", placeId);

        assertThat(countActivities(tripId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT place_id FROM trip_activity WHERE trip_id = ?", Long.class, tripId))
                .isNull();
    }

    @Test
    @DisplayName("FK 삭제 규칙이 스키마에 실제로 걸려 있다")
    void foreignKeyDeleteRules() {
        assertThat(deleteRuleOf("fk_trip_activity_trip")).isEqualTo("CASCADE");
        assertThat(deleteRuleOf("fk_trip_activity_place")).isEqualTo("SET NULL");
        // 목적지 장소는 여행이 참조 중이면 지워지면 안 된다. 삭제 규칙을 안 주면 InnoDB가
        // RESTRICT로 동작하지만 information_schema에는 NO ACTION으로 적힌다(같은 뜻).
        assertThat(deleteRuleOf("fk_trip_destination_place")).isEqualTo("NO ACTION");
    }

    @Test
    @DisplayName("조회 경로가 되는 인덱스가 전부 있다")
    void indexesExist() {
        assertThat(indexColumnsOf("trip", "idx_trip_member_start"))
                .containsExactly("member_id", "start_date");
        assertThat(indexColumnsOf("trip", "idx_trip_member_end"))
                .containsExactly("member_id", "end_date");
        // 보드 조회의 유일한 접근 경로.
        assertThat(indexColumnsOf("trip_activity", "idx_activity_trip_date_order"))
                .containsExactly("trip_id", "activity_date", "sort_order");
        assertThat(indexColumnsOf("travel_place", "uk_place_member_google"))
                .containsExactly("member_id", "google_place_id");
        assertThat(indexColumnsOf("travel_place", "idx_place_member_name"))
                .containsExactly("member_id", "name");
    }

    @Test
    @DisplayName("같은 구글 장소는 멤버당 하나지만, 직접 입력(NULL)은 여러 건 허용된다")
    @Transactional
    void uniqueIndexAllowsMultipleManualPlaces() {
        long memberId = insertMember("place-member");
        insertPlace(memberId, null, "골목 카페 A");
        insertPlace(memberId, null, "골목 카페 B");

        Integer manualCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM travel_place WHERE member_id = ? AND google_place_id IS NULL",
                Integer.class, memberId);

        assertThat(manualCount).isEqualTo(2);
    }

    @Test
    @DisplayName("일정 시각은 DATE·TIME이다 — DATETIME으로 바뀌면 타임존 변경 시 전 일정이 밀린다")
    void wallClockColumnTypes() {
        assertThat(columnTypeOf("trip_activity", "activity_date")).isEqualTo("date");
        assertThat(columnTypeOf("trip_activity", "start_time")).isEqualTo("time");
        assertThat(columnTypeOf("trip", "start_date")).isEqualTo("date");
        assertThat(columnTypeOf("trip", "end_date")).isEqualTo("date");
        // 여행 타임존은 IANA ID 문자열. 여기서 파생 계산의 기준이 나온다.
        assertThat(columnTypeOf("trip", "timezone")).isEqualTo("varchar");
    }

    @Test
    @DisplayName("불리언 컬럼은 BIT(1)이다(BOOLEAN=tinyint면 Hibernate validate가 깨진다)")
    void booleanColumnsAreBit() {
        assertThat(columnTypeOf("trip", "morning_summary_enabled")).isEqualTo("bit");
        assertThat(columnTypeOf("trip_activity", "notify_enabled")).isEqualTo("bit");
        assertThat(columnTypeOf("trip_activity", "departure_notify_enabled")).isEqualTo("bit");
        assertThat(columnTypeOf("travel_place", "manual_entry")).isEqualTo("bit");
    }

    @Test
    @DisplayName("일정을 지우면 기록도 함께 지워진다(ON DELETE CASCADE)")
    @Transactional
    void deletingActivityCascadesToLog() {
        long memberId = insertMember("log-cascade-member");
        long tripId = insertTrip(memberId, "도쿄");
        long activityId = insertActivity(tripId);
        jdbcTemplate.update("""
                INSERT INTO trip_activity_log (activity_id, memo, rating, created_at, updated_at)
                VALUES (?, '야경이 좋았다', 4, NOW(6), NOW(6))
                """, activityId);

        jdbcTemplate.update("DELETE FROM trip_activity WHERE id = ?", activityId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trip_activity_log WHERE activity_id = ?",
                Integer.class, activityId)).isZero();
    }

    @Test
    @DisplayName("일정당 기록은 하나뿐이다 — UNIQUE가 없으면 저장 재시도가 중복 행을 만든다")
    @Transactional
    void logIsUniquePerActivity() {
        long memberId = insertMember("log-unique-member");
        long activityId = insertActivity(insertTrip(memberId, "도쿄"));
        insertLog(activityId, 4);

        assertThatThrownBy(() -> insertLog(activityId, 5))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private long insertActivity(long tripId) {
        jdbcTemplate.update("""
                INSERT INTO trip_activity (trip_id, title, activity_date, sort_order,
                                           notify_enabled, departure_notify_enabled,
                                           created_at, updated_at)
                VALUES (?, '센소지', '2026-10-24', 0, b'0', b'0', NOW(6), NOW(6))
                """, tripId);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertLog(long activityId, int rating) {
        jdbcTemplate.update("""
                INSERT INTO trip_activity_log (activity_id, rating, created_at, updated_at)
                VALUES (?, ?, NOW(6), NOW(6))
                """, activityId, rating);
    }

    private long insertMember(String username) {
        jdbcTemplate.update(
                "INSERT INTO member (login_id, password, created_at, updated_at)"
                        + " VALUES (?, 'pw', NOW(6), NOW(6))", username);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertTrip(long memberId, String destination) {
        jdbcTemplate.update("""
                INSERT INTO trip (member_id, title, destination_name, start_date, end_date,
                                  timezone, currency, default_notify_minutes,
                                  morning_summary_enabled, created_at, updated_at)
                VALUES (?, ?, ?, '2026-10-24', '2026-10-27', 'Asia/Tokyo', 'JPY', 15, b'0',
                        NOW(6), NOW(6))
                """, memberId, destination, destination);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertPlace(long memberId, String googlePlaceId, String name) {
        jdbcTemplate.update("""
                INSERT INTO travel_place (member_id, google_place_id, name, manual_entry,
                                          created_at, updated_at)
                VALUES (?, ?, ?, b'0', NOW(6), NOW(6))
                """, memberId, googlePlaceId, name);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Integer countActivities(long tripId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trip_activity WHERE trip_id = ?", Integer.class, tripId);
    }

    private String deleteRuleOf(String constraintName) {
        return jdbcTemplate.queryForObject("""
                SELECT delete_rule FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE() AND constraint_name = ?
                """, String.class, constraintName);
    }

    private List<String> indexColumnsOf(String table, String indexName) {
        return jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?
                ORDER BY seq_in_index
                """, String.class, table, indexName);
    }

    private String columnTypeOf(String table, String column) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, table, column);
        return String.valueOf(row.get("data_type"));
    }
}
