package ds.project.orino.planner.travel.place.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 목적지 후보 걸러내기.
 *
 * <p>구글의 {@code includedType}은 힌트일 뿐이라 "파리"를 치면 파리바게뜨 지점이 먼저 온다.
 * 실제로 걸러내는 것은 응답의 {@code types}이고, 그 규칙이 이 기능의 전부다.
 */
class GooglePlacesClientTest {

    private static PlaceResult candidate(String name, String... types) {
        return new PlaceResult("id-" + name, name, name + " 주소",
                new BigDecimal("35.0"), new BigDecimal("139.0"),
                null, null, null, null, "Asia/Seoul", "KR", List.of(types));
    }

    @Nested
    @DisplayName("행정구역만 남긴다")
    class Filtering {

        @Test
        @DisplayName("빵집·공원은 목적지가 아니다 — '파리'를 치면 파리바게뜨가 먼저 온다")
        void dropsNonAdministrativePlaces() {
            List<PlaceResult> selected = GooglePlacesClient.selectDestinations(List.of(
                    candidate("파리", "point_of_interest", "service"),
                    candidate("파리바게뜨 대림현대점", "bakery", "food_store"),
                    candidate("파리15구공원", "park", "point_of_interest"),
                    candidate("아홍디쓰멍 드 파리", "administrative_area_level_3", "political")));

            assertThat(selected).extracting(PlaceResult::name)
                    .containsExactly("아홍디쓰멍 드 파리");
        }

        @Test
        @DisplayName("도시가 어느 단계로 잡히는지는 나라마다 다르다 — locality만으로는 못 거른다")
        void keepsAdministrativeLevelsBesidesLocality() {
            // 도쿄도는 admin_1, 오사카시는 locality다. 둘 다 목적지로 쓸 수 있어야 한다.
            List<PlaceResult> selected = GooglePlacesClient.selectDestinations(List.of(
                    candidate("도쿄도", "administrative_area_level_1", "political"),
                    candidate("오사카시", "locality", "political")));

            assertThat(selected).extracting(PlaceResult::name)
                    .containsExactlyInAnyOrder("도쿄도", "오사카시");
        }

        @Test
        @DisplayName("남는 게 없으면 빈 목록 — 없는 걸 지어내지 않는다")
        void returnsEmptyWhenNothingQualifies() {
            assertThat(GooglePlacesClient.selectDestinations(List.of(
                    candidate("파리바게뜨", "bakery")))).isEmpty();
        }
    }

    @Nested
    @DisplayName("좁은 행정구역을 먼저 보여준다")
    class Ordering {

        @Test
        @DisplayName("도시가 도·주보다 앞선다 — 여행 목적지는 보통 도시다")
        void narrowerFirst() {
            List<PlaceResult> selected = GooglePlacesClient.selectDestinations(List.of(
                    candidate("어느 나라", "country"),
                    candidate("어느 도", "administrative_area_level_1"),
                    candidate("어느 시", "locality")));

            assertThat(selected).extracting(PlaceResult::name)
                    .containsExactly("어느 시", "어느 도", "어느 나라");
        }

        @Test
        @DisplayName("후보가 많아도 5개까지만 — 목적지는 하나만 고른다")
        void capsResults() {
            List<PlaceResult> many = List.of(
                    candidate("A", "locality"), candidate("B", "locality"),
                    candidate("C", "locality"), candidate("D", "locality"),
                    candidate("E", "locality"), candidate("F", "locality"));

            assertThat(GooglePlacesClient.selectDestinations(many)).hasSize(5);
        }
    }
}
