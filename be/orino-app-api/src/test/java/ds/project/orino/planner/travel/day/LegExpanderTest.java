package ds.project.orino.planner.travel.day;

import ds.project.orino.planner.travel.day.service.LegExpander;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 구간 → 날짜 전개 규칙을 고정한다. <b>입력은 구간, 진실은 날짜</b>라는 v2.1의 전제가 여기서
 * 지켜지지 않으면 어떤 날짜는 도시가 없거나 두 도시를 갖는다.
 *
 * <p>합계와 기간이 어긋나도 저장을 막지 않는 것이 규칙이라, 어긋났을 때 무엇이 되는지가
 * 곧 사양이다.
 */
class LegExpanderTest {

    private static final LocalDate OCT24 = LocalDate.of(2026, 10, 24);
    private static final Long TOKYO = 1L;
    private static final Long NIKKO = 2L;
    private static final Long OSAKA = 3L;

    @Test
    @DisplayName("[도쿄 3][닛코 1][도쿄 2] → 날짜 6개, 도시 구간 3개")
    void expandsInOrder() {
        Map<LocalDate, Long> byDate = LegExpander.expand(OCT24, OCT24.plusDays(5), List.of(
                new LegExpander.Leg(TOKYO, 3),
                new LegExpander.Leg(NIKKO, 1),
                new LegExpander.Leg(TOKYO, 2)));

        assertThat(byDate).hasSize(6);
        assertThat(byDate.values()).containsExactly(TOKYO, TOKYO, TOKYO, NIKKO, TOKYO, TOKYO);
        assertThat(byDate.keySet()).containsExactly(
                OCT24, OCT24.plusDays(1), OCT24.plusDays(2),
                OCT24.plusDays(3), OCT24.plusDays(4), OCT24.plusDays(5));
    }

    @Test
    @DisplayName("합계가 기간보다 짧으면 남은 날짜가 마지막 도시를 이어 쓴다")
    void shortageInheritsLastCity() {
        // 합계 5일 / 기간 10일
        Map<LocalDate, Long> byDate = LegExpander.expand(OCT24, OCT24.plusDays(9), List.of(
                new LegExpander.Leg(TOKYO, 3),
                new LegExpander.Leg(NIKKO, 1),
                new LegExpander.Leg(OSAKA, 1)));

        assertThat(byDate).hasSize(10);
        assertThat(byDate.get(OCT24.plusDays(4))).isEqualTo(OSAKA);
        assertThat(byDate.get(OCT24.plusDays(9))).isEqualTo(OSAKA);
    }

    @Test
    @DisplayName("합계가 기간보다 길면 뒤 구간이 잘린다 — 저장을 막지 않는다")
    void excessTruncatesTailLegs() {
        // 합계 12일 / 기간 4일
        Map<LocalDate, Long> byDate = LegExpander.expand(OCT24, OCT24.plusDays(3), List.of(
                new LegExpander.Leg(TOKYO, 3),
                new LegExpander.Leg(NIKKO, 4),
                new LegExpander.Leg(OSAKA, 5)));

        assertThat(byDate).hasSize(4);
        assertThat(byDate.values()).containsExactly(TOKYO, TOKYO, TOKYO, NIKKO);
        assertThat(byDate.values()).doesNotContain(OSAKA);
    }

    @Test
    @DisplayName("구간이 하나면 전 날짜가 그 도시다")
    void singleLegFillsPeriod() {
        Map<LocalDate, Long> byDate = LegExpander.expand(OCT24, OCT24.plusDays(3),
                List.of(new LegExpander.Leg(TOKYO, 1)));

        assertThat(byDate.values()).containsOnly(TOKYO);
        assertThat(byDate).hasSize(4);
    }

    @Test
    @DisplayName("당일치기도 하루가 나온다")
    void singleDayTrip() {
        Map<LocalDate, Long> byDate = LegExpander.expand(OCT24, OCT24,
                List.of(new LegExpander.Leg(TOKYO, 3)));

        assertThat(byDate).containsExactly(Map.entry(OCT24, TOKYO));
    }

    @Test
    @DisplayName("구간 순서를 바꾸면 날짜 배치가 다시 계산된다")
    void reorderingLegsChangesDates() {
        Map<LocalDate, Long> before = LegExpander.expand(OCT24, OCT24.plusDays(3), List.of(
                new LegExpander.Leg(TOKYO, 2), new LegExpander.Leg(OSAKA, 2)));
        Map<LocalDate, Long> after = LegExpander.expand(OCT24, OCT24.plusDays(3), List.of(
                new LegExpander.Leg(OSAKA, 2), new LegExpander.Leg(TOKYO, 2)));

        assertThat(before.values()).containsExactly(TOKYO, TOKYO, OSAKA, OSAKA);
        assertThat(after.values()).containsExactly(OSAKA, OSAKA, TOKYO, TOKYO);
    }

    @Test
    @DisplayName("구간이 없으면 전개할 수 없다 — 타임존 없는 날짜를 만들지 않는다")
    void rejectsEmptyLegs() {
        assertThatThrownBy(() -> LegExpander.expand(OCT24, OCT24.plusDays(1), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("0일짜리 구간은 만들 수 없다 — 순서만 차지하고 하루도 갖지 않는다")
    void rejectsZeroDayLeg() {
        assertThatThrownBy(() -> new LegExpander.Leg(TOKYO, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
