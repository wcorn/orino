package ds.project.orino.planner.travel.board.controller;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.repository.TravelPlaceRepository;
import ds.project.orino.planner.travel.place.StubPlacesClient;
import ds.project.orino.planner.travel.place.client.PlaceResult;
import ds.project.orino.planner.travel.place.client.PlacesClient;
import ds.project.orino.planner.travel.tools.StubWeatherClient;
import ds.project.orino.planner.travel.tools.client.WeatherClient;
import ds.project.orino.planner.travel.tools.dto.WeatherIcon;
import ds.project.orino.planner.travel.tools.dto.WeatherResponse;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.StubExternalsConfig;
import ds.project.orino.support.TravelCityFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보드 응답 v2.1 — 날짜마다 기준 도시가 붙고, 날씨는 <b>도시별로 한 번씩</b> 조회한다.
 *
 * <p>날씨 호출 횟수를 세는 것이 이 테스트의 절반이다. 날짜마다 부르면 열흘짜리 여행이 열 번을
 * 부르는데, 화면으로는 구별되지 않고 <b>요금 고지서로만 드러난다.</b>
 */
// 스텁 조합을 새로 만들지 않는다 — 조합이 늘 때마다 Spring 컨텍스트가 하나씩 더 뜨고,
// 각각이 커넥션 풀을 물고 있어 전체 실행에서 OutOfMemoryError로 무너진다.
// 고정 시계는 쓰지 않는다: 모든 조회가 ?date=를 명시하므로 "오늘"에 기대지 않는다.
@Import(StubExternalsConfig.class)
class BoardV21Test extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TravelPlaceRepository placeRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private WeatherClient weatherClient;
    @Autowired
    private PlacesClient placesClient;

    private StubWeatherClient weatherStub;
    private String authHeader;
    private long tokyo;
    private long nikko;
    private String tokyoLat;

    /**
     * 날씨 캐시(Redis)는 테스트 사이에 살아 있다. 좌표가 같으면 앞 테스트가 캐시해 둔 예보가
     * 새어 들어와 <b>호출 횟수 검증이 무너진다</b> — 이 테스트의 절반이 그 숫자다.
     */
    private static String jitter(String base) {
        int nudge = Math.abs(UUID.randomUUID().hashCode() % 9000) + 1000;
        return new BigDecimal(base)
                .add(new BigDecimal("0.0000001").multiply(BigDecimal.valueOf(nudge)))
                .toPlainString();
    }

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        weatherStub = (StubWeatherClient) weatherClient;
        weatherStub.reset();
        placesStub().reset();
        placesStub().detailResult = Optional.empty();

        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        // 좌표가 있어야 날씨를 조회한다. 도시마다 다른 좌표를 줘야 캐시가 갈린다.
        tokyoLat = jitter("35.6812");
        tokyo = city("도쿄", tokyoLat, "139.7671");
        nikko = city("닛코", jitter("36.7199"), "139.6982");
    }

    @Nested
    @DisplayName("여행 헤더")
    class Header {

        @Test
        @DisplayName("전 기간 한 도시면 singleCity — 날짜 탭을 N일차로 그린다")
        void singleCityTrip() throws Exception {
            long tripId = createTrip(leg(tokyo, 4));

            board(tripId)
                    .andExpect(jsonPath("$.data.trip.singleCity").value(true))
                    .andExpect(jsonPath("$.data.trip.cityCount").value(1))
                    .andExpect(jsonPath("$.data.trip.countryCount").value(0));
        }

        @Test
        @DisplayName("도시가 둘이면 singleCity가 아니고 도시 수를 센다")
        void multiCityTrip() throws Exception {
            long tripId = createTrip(leg(tokyo, 2), leg(nikko, 2));

            board(tripId)
                    .andExpect(jsonPath("$.data.trip.singleCity").value(false))
                    .andExpect(jsonPath("$.data.trip.cityCount").value(2));
        }

        @Test
        @DisplayName("같은 도시를 다시 방문해도 도시 수는 하나다")
        void sameCityCountedOnce() throws Exception {
            long tripId = createTrip(leg(tokyo, 1), leg(nikko, 1), leg(tokyo, 2));

            board(tripId).andExpect(jsonPath("$.data.trip.cityCount").value(2));
        }
    }

    @Nested
    @DisplayName("날짜")
    class Days {

        @Test
        @DisplayName("날짜마다 기준 도시가 붙고 도시가 바뀌는 날짜에 cityChanged가 선다")
        void marksCityChange() throws Exception {
            long tripId = createTrip(leg(tokyo, 1), leg(nikko, 1), leg(tokyo, 2));

            board(tripId)
                    .andExpect(jsonPath("$.data.days", hasSize(4)))
                    .andExpect(jsonPath("$.data.days[0].baseCity.name").value("도쿄"))
                    // 첫날은 "바뀐 것"이 아니다 — 비교할 앞 날짜가 없다.
                    .andExpect(jsonPath("$.data.days[0].cityChanged").value(false))
                    .andExpect(jsonPath("$.data.days[1].baseCity.name").value("닛코"))
                    .andExpect(jsonPath("$.data.days[1].cityChanged").value(true))
                    .andExpect(jsonPath("$.data.days[2].cityChanged").value(true))
                    .andExpect(jsonPath("$.data.days[3].cityChanged").value(false))
                    // 도쿄 → 닛코 → 도쿄는 구간 3개다(같은 도시라도 사이가 끊기면 다른 구간).
                    .andExpect(jsonPath("$.data.days[0].legIndex").value(1))
                    .andExpect(jsonPath("$.data.days[1].legIndex").value(2))
                    .andExpect(jsonPath("$.data.days[3].legIndex").value(3));
        }

        @Test
        @DisplayName("날짜 id와 도시 메모가 함께 온다 — 롱프레스 시트가 그대로 쓴다")
        void carriesDayIdAndMemo() throws Exception {
            long tripId = createTrip(leg(tokyo, 4));
            long dayId = ((Number) JsonPath.read(boardBody(tripId), "$.data.days[1].dayId"))
                    .longValue();

            mockMvc.perform(put("/api/travel/days/" + dayId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"cityMemo\": \"코인로커\"}"))
                    .andExpect(status().isOk());

            board(tripId)
                    .andExpect(jsonPath("$.data.days[1].dayId").value(dayId))
                    .andExpect(jsonPath("$.data.days[1].cityMemo").value("코인로커"));
        }

        @Test
        @DisplayName("숙소 자리는 형태만 있고 값은 아직 없다 — FE가 두 번 고치지 않게")
        void stayFieldsAreNullUntilStage3() throws Exception {
            long tripId = createTrip(leg(tokyo, 4));

            board(tripId)
                    .andExpect(jsonPath("$.data.days[0].stayTonight").doesNotExist())
                    .andExpect(jsonPath("$.data.days[0].stayCheckout").doesNotExist())
                    .andExpect(jsonPath("$.data.stayMove").doesNotExist());
        }
    }

    @Nested
    @DisplayName("날씨 — 도시별로 한 번씩")
    class Weather {

        @Test
        @DisplayName("오사카 3일 여행에서 Open-Meteo 호출이 1회")
        void singleCityFetchesOnce() throws Exception {
            long tripId = createTrip(leg(tokyo, 4));

            board(tripId).andExpect(status().isOk());

            assertThat(weatherStub.calls).hasSize(1);
        }

        @Test
        @DisplayName("도쿄 → 닛코 → 도쿄는 2회 — 같은 도시는 캐시를 공유한다")
        void sameCitySharesCache() throws Exception {
            long tripId = createTrip(leg(tokyo, 1), leg(nikko, 1), leg(tokyo, 2));

            board(tripId).andExpect(status().isOk());

            assertThat(weatherStub.calls).hasSize(2);
        }

        @Test
        @DisplayName("날짜마다 그 도시의 예보가 붙는다")
        void spreadsForecastOverDates() throws Exception {
            LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Tokyo"));
            weatherStub.byCoordinates = coords -> forecast(today,
                    coords.startsWith(tokyoLat) ? 20 : 10);
            long tripId = createTripFrom(today, leg(tokyo, 1), leg(nikko, 1));

            board(tripId, today.toString())
                    .andExpect(jsonPath("$.data.days[0].weather.tempMax").value(20))
                    .andExpect(jsonPath("$.data.days[1].weather.tempMax").value(10));
        }
    }

    @Nested
    @DisplayName("도시 이탈 표시")
    class OutOfBaseCity {

        @Test
        @DisplayName("다른 도시의 장소면 outOfBaseCity가 선다 — 식별자로만 판정한다")
        void marksActivityInAnotherCity() throws Exception {
            long tripId = createTrip(leg(tokyo, 4));
            withCityRef(tokyo, "ChIJ_tokyo");
            // 기준 도시(도쿄)의 식별자와 다른 도시에 속한 장소.
            long placeId = poiInCity("오사카성", "ChIJ_osaka");
            createActivity(tripId, "오사카성", startDate(), placeId);

            board(tripId)
                    .andExpect(jsonPath("$.data.activities[0].outOfBaseCity").value(true))
                    .andExpect(jsonPath("$.data.activities[0].place.cityName").value("오사카"));
        }

        /**
         * 화면이 보관함을 도시별로 묶고 담기 시트를 정렬하려면 <b>기준 도시 쪽 식별자</b>가
         * 있어야 한다. 이름으로 묶으면 같은 글자를 쓰는 다른 도시가 한 덩어리가 된다.
         */
        @Test
        @DisplayName("기준 도시에도 도시 식별자가 실려 온다 — 화면이 같은 값으로 묶는다")
        void carriesBaseCityIdentifier() throws Exception {
            long tripId = createTrip(leg(tokyo, 4));
            withCityRef(tokyo, "ChIJ_tokyo");

            board(tripId)
                    .andExpect(jsonPath("$.data.days[0].baseCity.cityPlaceRef")
                            .value("ChIJ_tokyo"));
        }

        @Test
        @DisplayName("직접 입력한 도시에는 식별자가 없다 — 그런 장소는 묶지 않는다")
        void manualCityHasNoIdentifier() throws Exception {
            long tripId = createTrip(leg(tokyo, 4));

            board(tripId)
                    .andExpect(jsonPath("$.data.days[0].baseCity.cityPlaceRef")
                            .doesNotExist());
        }

        @Test
        @DisplayName("같은 도시의 장소면 서지 않는다")
        void sameCityIsNotFlagged() throws Exception {
            long tripId = createTrip(leg(tokyo, 4));
            withCityRef(tokyo, "ChIJ_tokyo");
            long placeId = poiInCity("센소지", "ChIJ_tokyo");
            createActivity(tripId, "센소지", startDate(), placeId);

            board(tripId)
                    .andExpect(jsonPath("$.data.activities[0].outOfBaseCity").value(false));
        }

        @Test
        @DisplayName("도시를 모르는 장소는 판정하지 않는다 — 모르는 것을 경고로 바꾸지 않는다")
        void unknownCityIsNotFlagged() throws Exception {
            long tripId = createTrip(leg(tokyo, 4));
            long placeId = poiInCity("이름만 아는 곳", null);
            createActivity(tripId, "직접 입력", startDate(), placeId);

            board(tripId)
                    .andExpect(jsonPath("$.data.activities[0].outOfBaseCity").value(false));
        }
    }

    @Nested
    @DisplayName("기준 도시 변경 — 검색 결과 그대로")
    class ChangeBaseCityFromSearch {

        @Test
        @DisplayName("담아 두지 않은 도시로도 하루를 옮긴다 — 서버가 담고 도시로 승격한다")
        void upsertsCityFromGoogle() throws Exception {
            long tripId = createTrip(leg(tokyo, 3));
            placesStub().detailResult = Optional.of(googleCity("ChIJ_kyoto", "교토", "JP"));

            changeCityByGoogleId(dayIdOf(tripId, 1), "ChIJ_kyoto")
                    .andExpect(jsonPath("$.data[1].baseCity.name").value("교토"))
                    .andExpect(jsonPath("$.data[1].baseCity.timezone").value("Asia/Tokyo"))
                    .andExpect(jsonPath("$.data[1].baseCity.currency").value("JPY"))
                    // 하루만 바꾸면 구간이 셋으로 쪼개진다. 정상이며 확인을 받지 않는다.
                    .andExpect(jsonPath("$.data[2].legIndex").value(3));
        }

        /**
         * 검색으로 고른 도시라야 <b>도시 식별자</b>가 생긴다. 화면이 먼저 장소를 만들어 보내면
         * 식별자가 없어 그날 일정이 전부 "다른 도시"로 표시된다 — 이 경로가 있는 이유다.
         */
        @Test
        @DisplayName("담긴 도시에 식별자가 붙어 도시 이탈 판정이 성립한다")
        void keepsCityIdentifier() throws Exception {
            long tripId = createTrip(leg(tokyo, 3));
            placesStub().detailResult = Optional.of(googleCity("ChIJ_kyoto", "교토", "JP"));
            changeCityByGoogleId(dayIdOf(tripId, 1), "ChIJ_kyoto");

            long placeId = poiInCity("오사카성", "ChIJ_osaka");
            createActivity(tripId, "오사카성", START.plusDays(1).toString(), placeId);

            board(tripId, START.plusDays(1).toString())
                    .andExpect(jsonPath("$.data.activities[0].outOfBaseCity").value(true));
        }

        @Test
        @DisplayName("이미 이 여행이 쓰던 도시를 다시 고르면 아무것도 바뀌지 않는다")
        void reusesSavedCity() throws Exception {
            long tripId = createTrip(leg(tokyo, 3));
            placesStub().reset();

            mockMvc.perform(put("/api/travel/days/" + dayIdOf(tripId, 1))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"baseCityPlaceId\": %d}".formatted(tokyo)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[1].baseCity.name").value("도쿄"))
                    .andExpect(jsonPath("$.data[1].cityChanged").value(false));
            assertThat(placesStub().detailFetches).isEmpty();
        }

        @Test
        @DisplayName("도시를 두 방식으로 동시에 지정하면 400 — 어느 쪽이 맞는지 정할 수 없다")
        void rejectsBothCityInputs() throws Exception {
            long tripId = createTrip(leg(tokyo, 3));

            mockMvc.perform(put("/api/travel/days/" + dayIdOf(tripId, 1))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"baseCityPlaceId": %d, "baseCityGooglePlaceId": "ChIJ_kyoto"}
                                    """.formatted(tokyo)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ---------------- helpers ----------------

    private static final LocalDate START = LocalDate.of(2026, 10, 24);

    private StubPlacesClient placesStub() {
        return (StubPlacesClient) placesClient;
    }

    private static PlaceResult googleCity(String googlePlaceId, String name, String countryCode) {
        return new PlaceResult(googlePlaceId, name, name, new BigDecimal("35.0116"),
                new BigDecimal("135.7681"), null, null, null, null,
                "Asia/Tokyo", name, countryCode, List.of());
    }

    /** 날짜 목록에서 {@code index}번째 날짜의 id. */
    private long dayIdOf(long tripId, int index) throws Exception {
        return ((Number) JsonPath.read(boardBody(tripId),
                "$.data.days[%d].dayId".formatted(index))).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions changeCityByGoogleId(
            long dayId, String googlePlaceId) throws Exception {
        return mockMvc.perform(put("/api/travel/days/" + dayId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseCityGooglePlaceId\": \"%s\"}".formatted(googlePlaceId)))
                .andExpect(status().isOk());
    }

    private static String startDate() {
        return START.toString();
    }

    private long city(String name, String lat, String lng) throws Exception {
        return TravelCityFixture.createCity(mockMvc, authHeader, name, "Asia/Tokyo", "JPY",
                lat, lng);
    }

    /** 도시 식별자를 가진 일반 장소. 도시 이탈 판정은 이 값만 본다. */
    private long poiInCity(String name, String cityPlaceRef) {
        Long memberId = memberRepository.findAll().get(0).getId();
        TravelPlace place = placeRepository.save(
                TravelPlace.fromGoogle(memberId, "g-" + UUID.randomUUID(), name));
        place.updateBasics(null, new BigDecimal("35.71"), new BigDecimal("139.79"), null, null);
        place.updateCityInfo(cityPlaceRef == null ? null : "오사카", cityPlaceRef, "JP");
        return placeRepository.saveAndFlush(place).getId();
    }

    /**
     * 도시에 식별자를 붙인다. <b>직접 입력한 도시는 구글 id가 없어 식별자도 없고</b>, 그때는
     * 이탈 판정 자체가 성립하지 않는다 — 검색으로 고른 도시라야 비교할 값이 생긴다.
     */
    private void withCityRef(long cityPlaceId, String cityPlaceRef) {
        TravelPlace city = placeRepository.findById(cityPlaceId).orElseThrow();
        city.updateCityInfo(city.getName(), cityPlaceRef, "JP");
        placeRepository.saveAndFlush(city);
    }

    private static String leg(long cityPlaceId, int days) {
        return "{\"cityPlaceId\": %d, \"days\": %d}".formatted(cityPlaceId, days);
    }

    private long createTrip(String... legs) throws Exception {
        return createTripFrom(START, legs);
    }

    private long createTripFrom(LocalDate start, String... legs) throws Exception {
        int total = legs.length == 0 ? 1 : legs.length;
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "여행", "startDate": "%s", "endDate": "%s",
                                 "legs": [%s]}
                                """.formatted(start, start.plusDays(daysOf(legs) - 1L),
                                        String.join(", ", legs))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(total).isPositive();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private static int daysOf(String... legs) {
        int sum = 0;
        for (String leg : legs) {
            sum += Integer.parseInt(leg.replaceAll(".*\"days\": (\\d+).*", "$1"));
        }
        return Math.max(1, sum);
    }

    private void createActivity(long tripId, String title, String date, long placeId)
            throws Exception {
        mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "activityDate": "%s", "placeId": %d}
                                """.formatted(title, date, placeId)))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions board(long tripId)
            throws Exception {
        return board(tripId, startDate());
    }

    private org.springframework.test.web.servlet.ResultActions board(long tripId, String date)
            throws Exception {
        return mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                        .param("date", date)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }

    private String boardBody(long tripId) throws Exception {
        return board(tripId).andReturn().getResponse().getContentAsString();
    }

    private static WeatherResponse forecast(LocalDate from, int tempMax) {
        List<WeatherResponse.DailyWeather> daily = List.of(
                new WeatherResponse.DailyWeather(from, WeatherIcon.CLEAR, tempMax, 5, 10),
                new WeatherResponse.DailyWeather(from.plusDays(1), WeatherIcon.CLEAR,
                        tempMax, 5, 10));
        return new WeatherResponse(WeatherResponse.SOURCE, WeatherResponse.LICENSE,
                java.time.Instant.now(), daily, java.util.Map.of());
    }
}
