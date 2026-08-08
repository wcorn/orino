package ds.project.orino.domain.planner.travel.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TripActivityLogTest {

    @Test
    @DisplayName("공백만 남은 메모는 없는 것과 같다 — 저장해두면 빈 기록이 있는 것처럼 보인다")
    void blankMemoBecomesNull() {
        TripActivityLog log = new TripActivityLog(1L, 4, "   ");

        assertThat(log.getMemo()).isNull();
    }

    @Test
    @DisplayName("평점을 null로 덮어쓸 수 있다 — 잘못 누른 별을 되돌릴 방법이 있어야 한다")
    void ratingCanBeCleared() {
        TripActivityLog log = new TripActivityLog(1L, 5, "좋았다");

        log.update(null, "좋았다");

        assertThat(log.getRating()).isNull();
        assertThat(log.getMemo()).isEqualTo("좋았다");
    }

    @Test
    @DisplayName("평점도 메모도 없으면 빈 기록이다")
    void emptyWhenNothingLeft() {
        TripActivityLog log = new TripActivityLog(1L, 3, "적어둔다");

        assertThat(log.isEmpty()).isFalse();

        log.update(null, "");

        assertThat(log.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("둘 중 하나만 있어도 빈 기록이 아니다")
    void notEmptyWithEitherValue() {
        assertThat(new TripActivityLog(1L, 1, null).isEmpty()).isFalse();
        assertThat(new TripActivityLog(1L, null, "메모만").isEmpty()).isFalse();
    }
}
