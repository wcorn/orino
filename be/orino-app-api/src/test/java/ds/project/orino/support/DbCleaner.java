package ds.project.orino.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DbCleaner {

    private static final String[] TABLES_IN_FK_ORDER = {
            "holiday",
            "day_plan_block",
            "routine_check",
            "review_calendar_mirror",
            "review_schedule",
            "flashcard",
            "note",
            "memo",
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
