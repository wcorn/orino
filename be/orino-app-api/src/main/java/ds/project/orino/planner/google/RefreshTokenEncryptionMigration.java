package ds.project.orino.planner.google;

import ds.project.orino.domain.planner.google.crypto.RefreshTokenEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 기존 평문 {@code google_account.refresh_token} 을 시작 시 1회 암호화한다(멱등).
 *
 * <p>JPA 컨버터를 우회해 원시 JDBC로 읽고, 이미 암호화된 값은 건너뛴다(이미 마이그레이션됨).
 * 단일 사용자라 사실상 0~1행. 운영 키(GOOGLE_REFRESH_TOKEN_KEY)가 안정적으로 설정된 상태에서 배포해야 한다.
 */
@Component
public class RefreshTokenEncryptionMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenEncryptionMigration.class);

    private final JdbcTemplate jdbcTemplate;
    private final RefreshTokenEncryptor encryptor;

    public RefreshTokenEncryptionMigration(JdbcTemplate jdbcTemplate, RefreshTokenEncryptor encryptor) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptor = encryptor;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList("SELECT id, refresh_token FROM google_account");

        int migrated = 0;
        for (Map<String, Object> row : rows) {
            String token = (String) row.get("refresh_token");
            if (token == null || encryptor.isEncrypted(token)) {
                continue;
            }
            jdbcTemplate.update(
                    "UPDATE google_account SET refresh_token = ? WHERE id = ?",
                    encryptor.encrypt(token), row.get("id"));
            migrated++;
        }
        if (migrated > 0) {
            log.info("refresh_token {}건을 암호화 마이그레이션했다", migrated);
        }
    }
}
