package ds.project.orino.planner.travel.day;

import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.TripDay;
import ds.project.orino.planner.travel.day.service.TransitionDays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 도시가 바뀌는 날의 파생 규칙을 고정한다. 이동일은 저장되지 않으므로(D-25) <b>이 계산이 곧
 * "그 하루가 두 도시에 속한다"의 정의</b>다.
 */
class TransitionDaysTest {

    private static final LocalDate OCT24 = LocalDate.of(2026, 10, 24);
    private static final Long OSAKA = 1L;
    private static final Long KYOTO = 2L;
    private static final Long TOKYO = 3L;

    @Test
    @DisplayName("도시가 바뀌는 날에 떠나온 도시가 붙는다 — 그 하루는 두 도시에 속한다")
    void marksDepartedCity() {
        Map<LocalDate, Long> departed =
                TransitionDays.departedByDate(days(OSAKA, OSAKA, KYOTO, KYOTO));

        assertThat(departed).containsExactly(Map.entry(OCT24.plusDays(2), OSAKA));
    }

    @Test
    @DisplayName("첫날은 바뀐 것이 아니다 — 비교할 앞 날짜가 없다")
    void firstDayIsNotATransition() {
        assertThat(TransitionDays.departedByDate(days(OSAKA, OSAKA)))
                .doesNotContainKey(OCT24);
    }

    /**
     * 당일치기가 박 기준 구간으로는 표현되지 않는 자리다 — 그날 밤은 도쿄에서 자므로 숙박으로
     * 보면 닛코가 사라진다. 날짜 기준이라 <b>앞뒤로 이동일이 두 번</b> 선다.
     */
    @Test
    @DisplayName("당일치기는 앞뒤 두 날이 모두 이동일이다")
    void dayTripMakesTwoTransitions() {
        Map<LocalDate, Long> departed =
                TransitionDays.departedByDate(days(TOKYO, KYOTO, TOKYO, TOKYO));

        assertThat(departed).containsExactly(
                Map.entry(OCT24.plusDays(1), TOKYO),
                Map.entry(OCT24.plusDays(2), KYOTO));
    }

    @Test
    @DisplayName("도시가 한 번도 안 바뀌면 이동일이 없다")
    void singleCityHasNoTransition() {
        assertThat(TransitionDays.departedByDate(days(OSAKA, OSAKA, OSAKA))).isEmpty();
    }

    @Test
    @DisplayName("날짜가 없으면 이동일도 없다")
    void emptyDays() {
        assertThat(TransitionDays.departedByDate(List.of())).isEmpty();
    }

    @Test
    @DisplayName("두 도시의 식별자를 모은다 — 이동일에 있어도 되는 곳이 둘이다")
    void collectsBothCityRefs() {
        assertThat(TransitionDays.cityRefsOf(cityRef("ChIJ_kyoto"), cityRef("ChIJ_osaka")))
                .containsExactly("ChIJ_kyoto", "ChIJ_osaka");
    }

    @Test
    @DisplayName("떠나온 도시가 없으면(이동일이 아니면) 도착 도시 하나뿐이다")
    void singleCityRefWhenNotTransition() {
        assertThat(TransitionDays.cityRefsOf(cityRef("ChIJ_kyoto"), null))
                .containsExactly("ChIJ_kyoto");
    }

    /**
     * 식별자가 없는 도시(직접 입력)는 비교할 값이 없다. 집합이 비면 판정 자체를 하지 않아야
     * 하므로, 여기서 빈 집합이 나오는 것이 곧 "판정하지 않는다"의 입구다(D-23).
     */
    @Test
    @DisplayName("식별자 없는 도시는 넣지 않는다 — 모르는 것으로 판정하지 않는다")
    void skipsCitiesWithoutIdentifier() {
        assertThat(TransitionDays.cityRefsOf(cityRef(null), null)).isEmpty();
    }

    @Test
    @DisplayName("같은 도시로 되돌아오는 날은 식별자가 하나로 합쳐진다")
    void deduplicatesSameCity() {
        assertThat(TransitionDays.cityRefsOf(cityRef("ChIJ_tokyo"), cityRef("ChIJ_tokyo")))
                .containsExactly("ChIJ_tokyo");
    }

    /** 10.24부터 하루씩, 주어진 순서대로 기준 도시를 붙인 날짜들. */
    private static List<TripDay> days(Long... basePlaceIds) {
        List<TripDay> days = new ArrayList<>();
        for (int i = 0; i < basePlaceIds.length; i++) {
            days.add(new TripDay(1L, OCT24.plusDays(i), basePlaceIds[i]));
        }
        return days;
    }

    private static TravelPlace cityRef(String cityPlaceRef) {
        TravelPlace city = TravelPlace.fromGoogle(1L, "g-city", "도시");
        city.updateCityInfo("도시", cityPlaceRef, "JP");
        return city;
    }
}
