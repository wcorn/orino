package ds.project.orino.planner.scheduling.engine.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeSlotTest {

    @Test
    @DisplayName("start와 end로 TimeSlot을 생성한다")
    void create() {
        TimeSlot slot = new TimeSlot(
                LocalTime.of(7, 0), LocalTime.of(10, 0));
        assertThat(slot.minutes()).isEqualTo(180);
    }

    @Test
    @DisplayName("start가 end보다 이전이 아니면 예외")
    void invalidRange() {
        assertThatThrownBy(() -> new TimeSlot(
                LocalTime.of(10, 0), LocalTime.of(7, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeSlot(
                LocalTime.of(10, 0), LocalTime.of(10, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null은 허용하지 않는다")
    void nullNotAllowed() {
        assertThatThrownBy(() -> new TimeSlot(null, LocalTime.of(1, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("시간 포함 여부를 판단한다")
    void contains() {
        TimeSlot slot = new TimeSlot(
                LocalTime.of(7, 0), LocalTime.of(10, 0));
        assertThat(slot.contains(LocalTime.of(7, 0))).isTrue();
        assertThat(slot.contains(LocalTime.of(8, 30))).isTrue();
        assertThat(slot.contains(LocalTime.of(10, 0))).isFalse();
        assertThat(slot.contains(LocalTime.of(6, 59))).isFalse();
    }

    @Test
    @DisplayName("슬롯 겹침을 판단한다")
    void overlaps() {
        TimeSlot a = new TimeSlot(
                LocalTime.of(7, 0), LocalTime.of(10, 0));
        TimeSlot b = new TimeSlot(
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        TimeSlot c = new TimeSlot(
                LocalTime.of(10, 0), LocalTime.of(12, 0));
        assertThat(a.overlaps(b)).isTrue();
        assertThat(a.overlaps(c)).isFalse();
    }
}
