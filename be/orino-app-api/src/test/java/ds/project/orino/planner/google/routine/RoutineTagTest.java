package ds.project.orino.planner.google.routine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutineTagTest {

    @Test
    @DisplayName("habit 태그는 orinoRoutine=1 + orinoRoutineType=habit")
    void habitProperties() {
        Map<String, String> props = RoutineTag.privateProperties(RoutineType.HABIT);

        assertThat(props)
                .containsEntry("orinoRoutine", "1")
                .containsEntry("orinoRoutineType", "habit");
    }

    @Test
    @DisplayName("schedule 태그는 orinoRoutineType=schedule")
    void scheduleProperties() {
        Map<String, String> props = RoutineTag.privateProperties(RoutineType.SCHEDULE);

        assertThat(props).containsEntry("orinoRoutineType", "schedule");
    }

    @Test
    @DisplayName("list 필터 값은 orinoRoutine=1")
    void listFilter() {
        assertThat(RoutineTag.LIST_FILTER).isEqualTo("orinoRoutine=1");
    }

    @Test
    @DisplayName("센티넬 보유 여부로 루틴 이벤트를 판별한다")
    void isRoutine() {
        assertThat(RoutineTag.isRoutine(Map.of("orinoRoutine", "1"))).isTrue();
        assertThat(RoutineTag.isRoutine(Map.of("other", "x"))).isFalse();
        assertThat(RoutineTag.isRoutine(null)).isFalse();
    }

    @Test
    @DisplayName("루틴 종류를 읽는다 (없거나 알 수 없으면 null)")
    void routineType() {
        assertThat(RoutineTag.routineType(RoutineTag.privateProperties(RoutineType.HABIT)))
                .isEqualTo(RoutineType.HABIT);
        assertThat(RoutineTag.routineType(Map.of("orinoRoutine", "1"))).isNull();
        assertThat(RoutineTag.routineType(Map.of("orinoRoutineType", "bogus"))).isNull();
        assertThat(RoutineTag.routineType(null)).isNull();
    }

    @Test
    @DisplayName("RoutineType.fromCode 왕복")
    void fromCode() {
        assertThat(RoutineType.fromCode("habit")).isEqualTo(RoutineType.HABIT);
        assertThat(RoutineType.fromCode("schedule")).isEqualTo(RoutineType.SCHEDULE);
        assertThatThrownBy(() -> RoutineType.fromCode("nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
