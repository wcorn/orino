package ds.project.orino.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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

    public DbCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
    }
}
