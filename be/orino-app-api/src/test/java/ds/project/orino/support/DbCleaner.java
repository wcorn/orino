package ds.project.orino.support;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 테스트 사이의 「깨끗한 상태」.
 *
 * <p><b>Redis도 함께 비운다.</b> Redis 컨테이너는 JVM 하나에 하나로 테스트 내내 살아 있어서
 * (`TestRedisConfig`), MySQL만 지우면 날씨·이동시간 캐시가 다음 테스트로 넘어간다. 그러면
 * 스텁을 비워 둔 테스트에 앞 테스트의 응답이 새어 들고, <b>외부 호출 횟수를 세는 단언이
 * 조용히 무너진다.</b>
 *
 * <p>한때 이 구멍을 테스트들이 각자 메우고 있었다 — 좌표를 난수로 흔들어 캐시 키를 갈라놓는
 * 방식이었는데, 경우의 수가 9000개뿐이라 26개짜리 클래스에서 <b>약 3%</b> 확률로 값이 겹쳤다.
 * 겹치는 날엔 캐시 히트가 한 번 더 생겨 CI가 코드와 무관하게 빨개졌다(#1294).
 * 확률에 기대는 우회책 대신 상태를 지우는 쪽이 맞다.
 */
@Component
public class DbCleaner {

    private static final String[] TABLES_IN_FK_ORDER = {
            // 가계부 — 자기참조(refund_of_id)와 자산 FK가 있어 원장부터 지운다.
            "ledger_installment_round",
            "ledger_installment",
            "ledger_transaction_receipt",
            "ledger_transaction_template",
            "ledger_transaction_tag",
            "ledger_transaction",
            "ledger_budget_category",
            "ledger_budget",
            "ledger_recurring_override",
            "ledger_recurring_amount_history",
            "ledger_recurring",
            "ledger_tag",
            "ledger_category",
            "ledger_statement",
            "ledger_asset",
            "ledger_asset_group",
            "ledger_settings",
            "shortlink_visit_daily",
            "shortlink_visit",
            "shortlink_target_history",
            "shortlink_tag",
            "shortlink",
            "push_notification",
            // FK_CHECKS=0이라 CASCADE가 안 돈다 — 자식 테이블을 직접 지워야 남지 않는다.
            "trip_activity_photo",
            "trip_activity_log",
            "trip_activity",
            "trip_stay",
            "trip_day",
            "trip",
            "travel_place",
            "push_subscription",
            "holiday",
            "day_plan_block",
            "routine_check",
            "review_calendar_mirror",
            "review_schedule",
            "flashcard",
            "note",
            "monthly_goal",
            "study_material",
            "google_account",
            "member"
    };

    private final JdbcTemplate jdbcTemplate;
    private final RedisConnectionFactory redisConnectionFactory;

    public DbCleaner(JdbcTemplate jdbcTemplate, RedisConnectionFactory redisConnectionFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    public void clean() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            for (String table : TABLES_IN_FK_ORDER) {
                jdbcTemplate.execute("DELETE FROM " + table);
            }
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
        cleanCache();
    }

    /** 캐시는 원장이 아니라서 순서를 따질 것이 없다 — 통째로 비운다. */
    private void cleanCache() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }
}
