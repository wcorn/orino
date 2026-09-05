package ds.project.orino.domain.planner.travel.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기한과 수량 규칙을 못 박는다. 둘 다 <b>저장하지 않거나 조용히 떨어뜨리는</b> 값이라
 * DB를 봐서는 틀린 것을 알 수 없다 — 여기가 유일한 안전망이다.
 */
class TripPrepItemTest {

    private static final LocalDate OCT24 = LocalDate.of(2026, 10, 24);

    private static TripPrepItem item(PrepCategory category) {
        return new TripPrepItem(1L, 1L, category, "숙소 잔금 결제", 0);
    }

    @Nested
    @DisplayName("기한은 D−N으로만 산다")
    class Due {

        @Test
        @DisplayName("기한 날짜는 출발일에서 뺀 값이다")
        void derivesDueDate() {
            TripPrepItem prep = item(PrepCategory.BOOKING);
            prep.changeDueDaysBefore(14);

            assertThat(prep.dueDate(OCT24)).isEqualTo(LocalDate.of(2026, 10, 10));
        }

        @Test
        @DisplayName("출발일이 하루 당겨지면 기한도 하루 당겨진다 — 고쳐 줄 행이 없다")
        void followsStartDate() {
            TripPrepItem prep = item(PrepCategory.BOOKING);
            prep.changeDueDaysBefore(14);

            // 날짜로 저장했다면 10/10에 그대로 남아 조용히 하루 늦은 기한이 됐을 자리다.
            assertThat(prep.dueDate(OCT24.minusDays(1)))
                    .isEqualTo(LocalDate.of(2026, 10, 9));
        }

        @Test
        @DisplayName("기한을 안 정한 항목은 날짜도 없고 지나지도 않는다")
        void noDueMeansNeverOverdue() {
            TripPrepItem prep = item(PrepCategory.TODO);

            assertThat(prep.dueDate(OCT24)).isNull();
            assertThat(prep.isOverdue(OCT24, LocalDate.of(2999, 1, 1))).isFalse();
        }

        @Test
        @DisplayName("기한 당일은 아직 지나지 않았다")
        void dueTodayIsNotOverdue() {
            TripPrepItem prep = item(PrepCategory.BOOKING);
            prep.changeDueDaysBefore(14);

            assertThat(prep.isOverdue(OCT24, LocalDate.of(2026, 10, 10))).isFalse();
            assertThat(prep.isOverdue(OCT24, LocalDate.of(2026, 10, 11))).isTrue();
        }

        @Test
        @DisplayName("체크한 항목은 기한이 지나도 경고하지 않는다 — 할 수 있는 일이 없다")
        void doneIsNeverOverdue() {
            TripPrepItem prep = item(PrepCategory.BOOKING);
            prep.changeDueDaysBefore(14);
            prep.check(true);

            assertThat(prep.isOverdue(OCT24, LocalDate.of(2026, 10, 11))).isFalse();
        }
    }

    @Nested
    @DisplayName("수량은 짐에서만 산다")
    class Quantity {

        @Test
        @DisplayName("짐이면 수량을 갖는다")
        void keepsQuantityForBag() {
            TripPrepItem prep = item(PrepCategory.BAG);
            prep.changeQuantity(4);

            assertThat(prep.getQuantity()).isEqualTo(4);
        }

        @Test
        @DisplayName("짐이 아니면 400이 아니라 NULL로 떨어진다")
        void dropsQuantityOutsideBag() {
            TripPrepItem prep = item(PrepCategory.TODO);
            prep.changeQuantity(4);

            assertThat(prep.getQuantity()).isNull();
        }

        @Test
        @DisplayName("짐에서 나가면 수량도 함께 사라진다")
        void clearsQuantityWhenLeavingBag() {
            TripPrepItem prep = item(PrepCategory.BAG);
            prep.changeQuantity(4);

            prep.changeCategory(PrepCategory.TODO, 7);

            assertThat(prep.getQuantity()).isNull();
            assertThat(prep.getDisplayOrder()).isEqualTo(7);
        }
    }
}
