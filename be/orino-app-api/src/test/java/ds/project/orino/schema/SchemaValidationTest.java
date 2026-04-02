package ds.project.orino.schema;

import ds.project.orino.config.TestRedisConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Liquibase changelog과 JPA 엔티티의 일치 여부를 검증한다.
 *
 * Liquibase가 changelog를 적용한 뒤, Hibernate validate가
 * 엔티티와 DB 스키마를 비교한다. 불일치 시 컨텍스트 로딩 실패 → 테스트 실패.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.liquibase.enabled=true",
        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml"
})
class SchemaValidationTest {

    @Test
    @DisplayName("Liquibase changelog이 JPA 엔티티와 일치한다")
    void changelogMatchesEntities() {
        // 컨텍스트 로딩 성공 = Liquibase 적용 + Hibernate validate 통과
    }
}
