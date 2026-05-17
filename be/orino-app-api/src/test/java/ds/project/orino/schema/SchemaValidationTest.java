package ds.project.orino.schema;

import ds.project.orino.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Liquibase changelog과 JPA 엔티티의 일치 여부를 검증한다.
 *
 * 테스트 프로파일이 Liquibase를 적용한 뒤 Hibernate validate가
 * 엔티티와 DB 스키마를 비교한다. 불일치 시 컨텍스트 로딩 실패 → 테스트 실패.
 */
@IntegrationTest
class SchemaValidationTest {

    @Test
    @DisplayName("Liquibase changelog이 JPA 엔티티와 일치한다")
    void changelogMatchesEntities() {
        // 컨텍스트 로딩 성공 = Liquibase 적용 + Hibernate validate 통과
    }
}
