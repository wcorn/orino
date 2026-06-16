package ds.project.orino.planner.google;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.google.crypto.RefreshTokenEncryptor;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.IntegrationTest;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class RefreshTokenEncryptionMigrationTest {

    @Autowired
    private RefreshTokenEncryptionMigration migration;
    @Autowired
    private RefreshTokenEncryptor encryptor;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private Long memberId;

    @BeforeEach
    void setUp() {
        dbCleaner.clean();
        memberId = memberRepository.save(MemberFixture.create()).getId();
    }

    private void insertPlaintextRow(String token) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO google_account
                    (member_id, refresh_token, connected_at, revoked, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)""",
                memberId, token, now, false, now, now);
    }

    private String storedRefreshToken() {
        return jdbcTemplate.queryForObject(
                "SELECT refresh_token FROM google_account WHERE member_id = ?", String.class, memberId);
    }

    @Test
    @DisplayName("평문 refresh_token row를 암호화하고, 재실행해도 그대로 둔다(멱등)")
    void migratesPlaintextAndIsIdempotent() {
        insertPlaintextRow("plain-refresh-token");

        migration.run(null);

        String afterFirst = storedRefreshToken();
        assertThat(afterFirst).isNotEqualTo("plain-refresh-token");
        assertThat(encryptor.isEncrypted(afterFirst)).isTrue();
        assertThat(encryptor.decrypt(afterFirst)).isEqualTo("plain-refresh-token");

        // 재실행: 이미 암호화됨 → 건너뛰어 값이 그대로
        migration.run(null);
        assertThat(storedRefreshToken()).isEqualTo(afterFirst);
    }
}
