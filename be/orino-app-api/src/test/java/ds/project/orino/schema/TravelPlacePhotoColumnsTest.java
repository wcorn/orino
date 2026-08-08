package ds.project.orino.schema;

import ds.project.orino.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 장소 사진 컬럼이 실제로 사라졌는지 본다(052).
 *
 * <p>Hibernate {@code validate}는 <b>남는 컬럼을 잡지 않는다</b> — 엔티티에서 필드만 지우고
 * 마이그레이션을 빠뜨려도 통과한다. 그래서 스키마를 직접 확인한다.
 */
@IntegrationTest
class TravelPlacePhotoColumnsTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("장소 사진 컬럼은 스키마에 없다 — 미도입 결정(D-16)")
    void photoColumnsAreGone() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'travel_place'
                """, String.class);

        assertThat(columns)
                .doesNotContain("photo_object_key")
                .doesNotContain("photo_attribution")
                // 같이 지워지면 안 되는 것들.
                .contains("opening_hours", "details_refreshed_at");
    }
}
