package ds.project.orino.planner.lifelog.moment.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedCursorTest {

    @Test
    @DisplayName("encode/decode 왕복 - micros 정밀도까지 보존한다")
    void roundTripPreservesMicros() {
        FeedCursor original = new FeedCursor(Instant.parse("2026-07-20T05:30:00.123456Z"), 4242L);

        FeedCursor decoded = FeedCursor.decode(original.encode());

        assertThat(decoded.occurredAt()).isEqualTo(Instant.parse("2026-07-20T05:30:00.123456Z"));
        assertThat(decoded.id()).isEqualTo(4242L);
    }

    @Test
    @DisplayName("null/blank는 첫 페이지(null)")
    void nullMeansFirstPage() {
        assertThat(FeedCursor.decode(null)).isNull();
        assertThat(FeedCursor.decode("  ")).isNull();
    }

    @Test
    @DisplayName("깨진 커서는 400")
    void invalidCursorIsBadRequest() {
        assertThatThrownBy(() -> FeedCursor.decode("!!!not-base64!!!"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }
}
