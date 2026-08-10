package ds.project.orino.planner.travel.day;

import ds.project.orino.domain.planner.travel.entity.TripDay;
import ds.project.orino.planner.travel.day.service.LegDeriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 날짜 → 구간 파생 규칙을 고정한다. 구간은 저장하지 않으므로(D-21) <b>이 계산이 곧 구간의
 * 정의</b>다 — 틀리면 DB를 봐도 알 수 없다.
 */
class LegDeriverTest {

    private static final LocalDate OCT24 = LocalDate.of(2026, 10, 24);
    private static final Long TOKYO = 1L;
    private static final Long NIKKO = 2L;
    private static final Long OSAKA = 3L;

    @Test
    @DisplayName("전 기간 같은 도시면 구간은 하나다")
    void singleCityIsOneLeg() {
        List<LegDeriver.DerivedLeg> legs = LegDeriver.derive(
                days(TOKYO, TOKYO, TOKYO, TOKYO));

        assertThat(legs).singleElement().satisfies(leg -> {
            assertThat(leg.legIndex()).isEqualTo(1);
            assertThat(leg.basePlaceId()).isEqualTo(TOKYO);
            assertThat(leg.days()).isEqualTo(4);
            assertThat(leg.startDate()).isEqualTo(OCT24);
            assertThat(leg.endDate()).isEqualTo(OCT24.plusDays(3));
        });
    }

    @Test
    @DisplayName("[도쿄, 닛코, 도쿄] → 구간 3개로 쪼개진다 — 같은 도시라도 사이가 끊기면 다른 구간이다")
    void sameCityAfterAnotherIsNewLeg() {
        List<LegDeriver.DerivedLeg> legs = LegDeriver.derive(days(TOKYO, NIKKO, TOKYO));

        assertThat(legs).hasSize(3);
        assertThat(legs).extracting(LegDeriver.DerivedLeg::basePlaceId)
                .containsExactly(TOKYO, NIKKO, TOKYO);
        assertThat(legs).extracting(LegDeriver.DerivedLeg::legIndex)
                .containsExactly(1, 2, 3);
        assertThat(legs).extracting(LegDeriver.DerivedLeg::days)
                .containsExactly(1, 1, 1);
    }

    @Test
    @DisplayName("연속된 같은 도시는 한 구간으로 묶이고 일수가 합쳐진다")
    void consecutiveDaysMerge() {
        List<LegDeriver.DerivedLeg> legs = LegDeriver.derive(
                days(OSAKA, OSAKA, OSAKA, NIKKO, TOKYO, TOKYO));

        assertThat(legs).extracting(LegDeriver.DerivedLeg::days).containsExactly(3, 1, 2);
        assertThat(legs.get(0).endDate()).isEqualTo(OCT24.plusDays(2));
        assertThat(legs.get(1).startDate()).isEqualTo(OCT24.plusDays(3));
        assertThat(legs.getLast().endDate()).isEqualTo(OCT24.plusDays(5));
    }

    @Test
    @DisplayName("날짜가 하나면 구간도 하나, 하루짜리다")
    void singleDay() {
        List<LegDeriver.DerivedLeg> legs = LegDeriver.derive(days(TOKYO));

        assertThat(legs).singleElement()
                .satisfies(leg -> assertThat(leg.days()).isEqualTo(1));
    }

    @Test
    @DisplayName("날짜가 없으면 구간도 없다")
    void emptyDays() {
        assertThat(LegDeriver.derive(List.of())).isEmpty();
    }

    /** 10.24부터 하루씩, 주어진 순서대로 기준 도시를 붙인 날짜들. */
    private static List<TripDay> days(Long... basePlaceIds) {
        List<TripDay> days = new java.util.ArrayList<>();
        for (int i = 0; i < basePlaceIds.length; i++) {
            days.add(new TripDay(1L, OCT24.plusDays(i), basePlaceIds[i]));
        }
        return days;
    }
}
