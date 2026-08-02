package ds.project.orino.common.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StudyDay - 하루의 경계는 새벽 4시")
class StudyDayTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static Instant atSeoul(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(SEOUL).toInstant();
    }

    @ParameterizedTest(name = "{0} → 학습일 {1}")
    @CsvSource({
            // 자정~04:00은 아직 전날 몫
            "2026-06-10T00:00, 2026-06-09",
            "2026-06-10T01:00, 2026-06-09",
            "2026-06-10T03:59, 2026-06-09",
            // 04:00부터 새 학습일
            "2026-06-10T04:00, 2026-06-10",
            "2026-06-10T04:01, 2026-06-10",
            "2026-06-10T12:00, 2026-06-10",
            "2026-06-10T23:59, 2026-06-10"
    })
    @DisplayName("자정~04:00은 전날, 04:00부터 새 학습일")
    void of(String localDateTime, String expected) {
        assertThat(StudyDay.of(atSeoul(localDateTime), SEOUL)).isEqualTo(LocalDate.parse(expected));
    }

    @Test
    @DisplayName("학습일의 시작은 그 날짜 04:00")
    void startOf() {
        assertThat(StudyDay.startOf(LocalDate.of(2026, 6, 10), SEOUL))
                .isEqualTo(atSeoul("2026-06-10T04:00"));
    }

    @Test
    @DisplayName("04:00 정각은 경계를 바꿔도 같은 날짜로 남는다 — 쌓인 scheduled_at은 그대로다")
    void rollover_instants_map_to_themselves() {
        LocalDate date = LocalDate.of(2026, 6, 10);

        assertThat(StudyDay.of(StudyDay.startOf(date, SEOUL), SEOUL)).isEqualTo(date);
    }

    @Test
    @DisplayName("경계는 사용자 시간대 기준이다")
    void boundary_is_user_timezone_based() {
        // 서울 6/10 04:00 = UTC 6/9 19:00
        assertThat(StudyDay.startOf(LocalDate.of(2026, 6, 10), SEOUL))
                .isEqualTo(Instant.parse("2026-06-09T19:00:00Z"));
    }
}
