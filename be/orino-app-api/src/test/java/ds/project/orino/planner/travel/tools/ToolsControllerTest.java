package ds.project.orino.planner.travel.tools;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.planner.travel.tools.client.EcbRates;
import ds.project.orino.planner.travel.tools.client.EcbRatesClient;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S-08 도구 — 날씨·환율 프록시. */
@Import(StubExternalsConfig.class)
class ToolsControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private WeatherClient weatherClient;
    @Autowired
    private EcbRatesClient ratesClient;

    private StubWeatherClient weatherStub;
    private StubEcbRatesClient ratesStub;
    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        weatherStub = (StubWeatherClient) weatherClient;
        ratesStub = (StubEcbRatesClient) ratesClient;
        weatherStub.reset();
        ratesStub.reset();

        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    /**
     * 좌표를 흔들어 캐시가 테스트 사이에 새지 않게 한다 — Redis 컨테이너는 계속 살아 있고
     * 캐시 키가 좌표라서다.
     */
    private static BigDecimal jitter(String base) {
        int nudge = Math.abs(UUID.randomUUID().hashCode() % 9000) + 1000;
        return new BigDecimal(base).add(
                new BigDecimal("0.0000001").multiply(BigDecimal.valueOf(nudge)));
    }

    private long createTrip(boolean withCoordinates) throws Exception {
        // 날씨 좌표는 여행이 아니라 기준 도시가 갖는다. 좌표가 없는 도시면 날씨도 없다.
        long cityId = withCoordinates
                ? TravelCityFixture.createCity(mockMvc, authHeader, "도쿄", "Asia/Tokyo", "JPY",
                        jitter("35.6764").toPlainString(), jitter("139.6500").toPlainString())
                : TravelCityFixture.createCity(mockMvc, authHeader, "도쿄", "Asia/Tokyo", "JPY");
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "도쿄",
                                 "startDate": "2026-10-24", "endDate": "2026-10-26",
                                 %s}
                                """.formatted(TravelCityFixture.singleLeg(cityId, 3))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.id")).longValue();
    }

    private static WeatherResponse.DailyWeather day(String date, WeatherIcon icon,
                                                    int max, int min, int precip) {
        return new WeatherResponse.DailyWeather(LocalDate.parse(date), icon, max, min, precip);
    }

    @Nested
    @DisplayName("날씨")
    class Weather {

        @Test
        @DisplayName("출처와 라이선스를 함께 준다 — Open-Meteo는 표기가 필수다")
        void carriesAttribution() throws Exception {
            long tripId = createTrip(true);

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/weather")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.source").value("Open-Meteo"))
                    .andExpect(jsonPath("$.data.license").value("CC BY 4.0"));
        }

        @Test
        @DisplayName("여행 기간만 남긴다 — 예보 범위 안이어도 여행 밖 날짜는 쓸 일이 없다")
        void clampsToTripPeriod() throws Exception {
            weatherStub.withDays(List.of(
                    day("2026-10-23", WeatherIcon.CLEAR, 18, 9, 10),   // 여행 전날
                    day("2026-10-24", WeatherIcon.CLEAR, 15, 8, 20),
                    day("2026-10-25", WeatherIcon.RAIN, 14, 9, 80),
                    day("2026-10-27", WeatherIcon.CLOUD, 16, 8, 30)),  // 여행 다음날
                    Map.of());
            long tripId = createTrip(true);

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/weather")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.daily", hasSize(2)))
                    .andExpect(jsonPath("$.data.daily[0].date").value("2026-10-24"))
                    .andExpect(jsonPath("$.data.daily[1].precipProbability").value(80));
        }

        @Test
        @DisplayName("예보 범위 밖이면 빈 배열 — 오류가 아니다")
        void emptyWhenOutOfRange() throws Exception {
            long tripId = createTrip(true);

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/weather")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.daily", hasSize(0)));
        }

        @Test
        @DisplayName("조회에 실패해도 200 — 날씨 때문에 화면이 죽지 않는다")
        void survivesUpstreamFailure() throws Exception {
            weatherStub.result = Optional.empty();
            long tripId = createTrip(true);

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/weather")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.daily", hasSize(0)));
        }

        @Test
        @DisplayName("좌표가 없으면 외부를 부르지도 않는다")
        void skipsWithoutCoordinates() throws Exception {
            long tripId = createTrip(false);

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/weather")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            assertThat(weatherStub.calls).isEmpty();
        }

        @Test
        @DisplayName("두 번 물어도 외부 호출은 한 번 — 예보는 6시간마다 바뀐다")
        void cachesForecast() throws Exception {
            long tripId = createTrip(true);

            for (int i = 0; i < 3; i++) {
                mockMvc.perform(get("/api/travel/trips/" + tripId + "/weather")
                        .header(HttpHeaders.AUTHORIZATION, authHeader));
            }

            assertThat(weatherStub.calls).hasSize(1);
        }

        @Test
        @DisplayName("남의 여행은 404")
        void rejectsOtherMembersTrip() throws Exception {
            long tripId = createTrip(true);
            memberRepository.save(MemberFixture.create("other", "password"));
            String otherAuth = "Bearer "
                    + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/weather")
                            .header(HttpHeaders.AUTHORIZATION, otherAuth))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("보드 날짜 탭")
    class BoardWeather {

        @Test
        @DisplayName("날짜별 요약이 탭에 붙는다 — 예보 없는 날은 null")
        void attachesToDays() throws Exception {
            weatherStub.withDays(List.of(
                    day("2026-10-24", WeatherIcon.RAIN, 14, 9, 80)), Map.of());
            long tripId = createTrip(true);

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.days[0].weather.icon").value("RAIN"))
                    .andExpect(jsonPath("$.data.days[0].weather.precipProbability").value(80))
                    // 예보가 없는 날짜는 비어 있다.
                    .andExpect(jsonPath("$.data.days[1].weather").doesNotExist());
        }
    }

    @Nested
    @DisplayName("환율")
    class ExchangeRate {

        @Test
        @DisplayName("EUR 기준 고시에서 교차환산한다 — JPY↔KRW는 직접 값이 없다")
        void crossConverts() throws Exception {
            mockMvc.perform(get("/api/travel/fx")
                            .param("base", "JPY").param("quote", "KRW")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    // 1600.00 ÷ 182.64 = 8.7604...
                    .andExpect(jsonPath("$.data.rate").value(8.7604))
                    .andExpect(jsonPath("$.data.source").value("ECB"))
                    .andExpect(jsonPath("$.data.referenceDate").value("2026-08-07"));
        }

        @Test
        @DisplayName("EUR은 고시표에 없지만 1로 다룬다")
        void handlesEuroItself() throws Exception {
            mockMvc.perform(get("/api/travel/fx")
                            .param("base", "EUR").param("quote", "JPY")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.rate").value(182.64));
        }

        @Test
        @DisplayName("같은 통화면 1")
        void sameCurrencyIsOne() throws Exception {
            mockMvc.perform(get("/api/travel/fx")
                            .param("base", "JPY").param("quote", "JPY")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.rate").value(1.0));
        }

        @Test
        @DisplayName("고시표에 없는 통화는 400 — 값이 없는 것과 서비스가 죽은 건 다르다")
        void rejectsUnknownCurrency() throws Exception {
            mockMvc.perform(get("/api/travel/fx")
                            .param("base", "JPY").param("quote", "XYZ")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-011"));
        }

        // 고시 실패·캐시 동작은 ExchangeRateServiceTest에서 본다 —
        // 고시표 캐시는 통화쌍과 무관한 전역 키라 여기서는 상태를 통제할 수 없다.
    }

    @Nested
    @DisplayName("교차환산 계산")
    class Cross {

        private final EcbRates rates = new EcbRates(LocalDate.parse("2026-08-07"),
                Map.of("JPY", new BigDecimal("182.64"), "KRW", new BigDecimal("1600.00")));

        @Test
        @DisplayName("방향을 뒤집으면 역수가 된다")
        void isReciprocal() {
            BigDecimal forward = rates.cross("JPY", "KRW").orElseThrow();
            BigDecimal backward = rates.cross("KRW", "JPY").orElseThrow();

            assertThat(forward.multiply(backward).doubleValue()).isCloseTo(
                    1.0, org.assertj.core.data.Offset.offset(0.0001));
        }

        @Test
        @DisplayName("모르는 통화면 빈 값")
        void unknownCurrency() {
            assertThat(rates.cross("JPY", "XYZ")).isEmpty();
        }
    }
}
