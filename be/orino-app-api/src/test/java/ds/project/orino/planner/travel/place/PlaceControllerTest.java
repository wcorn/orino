package ds.project.orino.planner.travel.place;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.repository.TravelPlaceRepository;
import ds.project.orino.planner.travel.place.client.PlaceResult;
import ds.project.orino.planner.travel.place.client.PlacesClient;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.StubExternalsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 장소 프록시 통합 테스트. 외부 호출만 스텁으로 갈아끼우고 캐시(Redis)·저장(MySQL)은 실물을 쓴다 —
 * 캐시가 정말 호출을 줄이는지는 실제 Redis 없이는 확인되지 않는다.
 */
@Import(StubExternalsConfig.class)
class PlaceControllerTest extends ApiTestSupport {

    /**
     * 캐시(Redis)는 테스트 사이에 살아 있다. 지우는 대신 <b>검색어를 테스트마다 다르게</b> 둔다 —
     * 키가 겹치지 않으면 격리가 필요 없고, 캐시 동작 자체도 그대로 검증된다.
     */
    private static String uniqueQuery(String label) {
        return label + "-" + java.util.UUID.randomUUID();
    }


    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TravelPlaceRepository placeRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private PlacesClient placesClient;

    private StubPlacesClient stub;
    private String authHeader;
    private String otherAuthHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        stub = (StubPlacesClient) placesClient;
        stub.reset();
        stub.cityResults = List.of();
        stub.placeResults = List.of();
        stub.detailResult = Optional.empty();
        // 스텁은 필드를 공유한다 — 앞 테스트가 비워 뒀으면 되돌려 놓는다.
        stub.photoResult = Optional.of(new byte[] {1, 2, 3});

        memberRepository.save(MemberFixture.create());
        memberRepository.save(MemberFixture.create("other", "password"));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        otherAuthHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");
    }

    private static PlaceResult city(String id, String name, String tz, String country) {
        return new PlaceResult(id, name, "일본 도쿄도", new BigDecimal("35.6762"),
                new BigDecimal("139.6503"), null, null, null, null, tz, country,
                List.of("locality", "political"), null, null);
    }

    private static PlaceResult place(String id, String name) {
        return new PlaceResult(id, name, "도쿄도 다이토구", new BigDecimal("35.7147"),
                new BigDecimal("139.7966"), "사찰", new BigDecimal("4.5"),
                "+81 3-3842-0181", "{\"weekdayDescriptions\":[\"월: 06:00~17:00\"]}",
                "Asia/Tokyo", "JP", List.of("tourist_attraction"),
                "places/p1/photos/ph1", "구글 사용자");
    }

    @Nested
    @DisplayName("목적지 검색")
    class Cities {

        @Test
        @DisplayName("타임존과 통화를 서버가 확정해서 준다")
        void resolvesTimezoneAndCurrency() throws Exception {
            stub.cityResults = List.of(city("ChIJ_tokyo", "도쿄도", "Asia/Tokyo", "JP"));

            mockMvc.perform(get("/api/travel/places/cities").param("q", uniqueQuery("도쿄"))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("도쿄도"))
                    .andExpect(jsonPath("$.data[0].timezone").value("Asia/Tokyo"))
                    // 국가 코드에서 유도한다 — 매핑 테이블을 두지 않는다.
                    .andExpect(jsonPath("$.data[0].currency").value("JPY"));
        }

        @Test
        @DisplayName("나라마다 통화가 달라진다")
        void derivesCurrencyPerCountry() throws Exception {
            stub.cityResults = List.of(
                    city("ChIJ_paris", "파리", "Europe/Paris", "FR"),
                    city("ChIJ_ny", "뉴욕", "America/New_York", "US"));

            mockMvc.perform(get("/api/travel/places/cities").param("q", uniqueQuery("도시"))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data[0].currency").value("EUR"))
                    .andExpect(jsonPath("$.data[1].currency").value("USD"));
        }

        @Test
        @DisplayName("타임존을 못 얻은 후보는 버린다 — 목적지로 쓸 수 없다")
        void dropsCandidatesWithoutTimezone() throws Exception {
            stub.cityResults = List.of(
                    city("ChIJ_ok", "도쿄도", "Asia/Tokyo", "JP"),
                    city("ChIJ_bad", "어딘가", null, "JP"));

            mockMvc.perform(get("/api/travel/places/cities").param("q", uniqueQuery("도쿄"))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].googlePlaceId").value("ChIJ_ok"));
        }

        @Test
        @DisplayName("같은 검색어를 다시 치면 캐시가 받아 외부 호출이 늘지 않는다")
        void cachesCitySearch() throws Exception {
            stub.cityResults = List.of(city("ChIJ_tokyo", "도쿄도", "Asia/Tokyo", "JP"));
            String query = uniqueQuery("도쿄");

            for (int i = 0; i < 3; i++) {
                mockMvc.perform(get("/api/travel/places/cities").param("q", query)
                                .header(HttpHeaders.AUTHORIZATION, authHeader))
                        .andExpect(status().isOk());
            }

            // Places는 호출당 과금이라 이게 곧 비용이다.
            assertThat(stub.citySearches).hasSize(1);
        }
    }

    @Nested
    @DisplayName("장소 검색")
    class Search {

        @Test
        @DisplayName("아직 담지 않은 곳은 사진이 없다 — 검색 20건의 사진을 여기서 받지 않는다")
        void returnsResults() throws Exception {
            stub.placeResults = List.of(place("ChIJ_senso", "센소지"));

            mockMvc.perform(get("/api/travel/places/search").param("q", uniqueQuery("센소지"))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("센소지"))
                    .andExpect(jsonPath("$.data[0].category").value("사찰"))
                    .andExpect(jsonPath("$.data[0].rating").value(4.5))
                    .andExpect(jsonPath("$.data[0].id").doesNotExist())
                    .andExpect(jsonPath("$.data[0].photoUrl").doesNotExist())
                    .andExpect(jsonPath("$.data[0].loved").value(false));
        }

        @Test
        @DisplayName("이미 담아 둔 장소는 내부 id를 실어 준다")
        void marksAlreadySavedPlaces() throws Exception {
            Long memberId = memberRepository.findAll().get(0).getId();
            TravelPlace saved = placeRepository.save(
                    TravelPlace.fromGoogle(memberId, "ChIJ_senso", "센소지"));
            stub.placeResults = List.of(place("ChIJ_senso", "센소지"));

            mockMvc.perform(get("/api/travel/places/search").param("q", uniqueQuery("센소지"))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data[0].id").value(saved.getId().intValue()));
        }

        @Test
        @DisplayName("담아 둔 장소는 캐시된 사진을 실어 준다 — 이미 받아 둔 것은 공짜다")
        void carriesCachedPhotoForSavedPlaces() throws Exception {
            Long memberId = memberRepository.findAll().get(0).getId();
            TravelPlace saved = placeRepository.save(
                    TravelPlace.fromGoogle(memberId, "ChIJ_senso", "센소지"));
            saved.updateDetails(null, null, "travel/places/1/a.jpg", "구글 사용자",
                    java.time.Instant.now());
            placeRepository.saveAndFlush(saved);
            stub.placeResults = List.of(place("ChIJ_senso", "센소지"));

            mockMvc.perform(get("/api/travel/places/search").param("q", uniqueQuery("센소지"))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data[0].photoUrl")
                            .value(org.hamcrest.Matchers.endsWith("travel/places/1/a.jpg")));

            // 검색은 사진을 새로 받지 않는다.
            assertThat(stub.photoFetches).isEmpty();
        }

        @Test
        @DisplayName("남이 담아 둔 장소는 내 검색 결과에 id로 붙지 않는다")
        void savedIdIsScopedByMember() throws Exception {
            Long other = memberRepository.findAll().get(1).getId();
            placeRepository.save(TravelPlace.fromGoogle(other, "ChIJ_senso", "센소지"));
            stub.placeResults = List.of(place("ChIJ_senso", "센소지"));

            mockMvc.perform(get("/api/travel/places/search").param("q", uniqueQuery("센소지"))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data[0].id").doesNotExist());
        }

        @Test
        @DisplayName("tripId를 주면 그 여행의 목적지 좌표로 검색을 편향시킨다")
        void biasesTowardTripDestination() throws Exception {
            long tripId = createTripWithCoordinates();
            stub.placeResults = List.of(place("ChIJ_senso", "센소지"));

            mockMvc.perform(get("/api/travel/places/search")
                            .param("q", uniqueQuery("라멘")).param("tripId", String.valueOf(tripId))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            assertThat(stub.biases).hasSize(1);
            assertThat(stub.biases.get(0)).isNotNull();
            assertThat(stub.biases.get(0).lat()).isEqualByComparingTo("35.6762");
        }

        @Test
        @DisplayName("tripId가 없으면 편향 없이 검색한다")
        void searchesWithoutBias() throws Exception {
            stub.placeResults = List.of(place("ChIJ_senso", "센소지"));

            mockMvc.perform(get("/api/travel/places/search").param("q", uniqueQuery("라멘"))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            assertThat(stub.biases).containsExactly((PlacesClient.Coordinates) null);
        }

        @Test
        @DisplayName("빈 결과는 캐시하지 않는다 — 일시적 실패를 한 시간 물고 있으면 안 된다")
        void doesNotCacheEmptyResults() throws Exception {
            String query = uniqueQuery("없는곳");
            stub.placeResults = List.of();
            mockMvc.perform(get("/api/travel/places/search").param("q", query)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data", hasSize(0)));

            // 복구된 뒤 다시 치면 결과가 나와야 한다.
            stub.placeResults = List.of(place("ChIJ_senso", "센소지"));
            mockMvc.perform(get("/api/travel/places/search").param("q", query)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data", hasSize(1)));

            assertThat(stub.placeSearches).hasSize(2);
        }
    }

    @Nested
    @DisplayName("상세 · 직접 입력")
    class DetailAndManual {

        @Test
        @DisplayName("직접 입력한 장소가 manualEntry로 저장된다")
        void createsManualPlace() throws Exception {
            mockMvc.perform(post("/api/travel/places")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "숙소 근처 골목 카페", "address": "어딘가"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("숙소 근처 골목 카페"))
                    .andExpect(jsonPath("$.data.manualEntry").value(true))
                    .andExpect(jsonPath("$.data.googlePlaceId").doesNotExist());
        }

        @Test
        @DisplayName("이름이 없으면 400")
        void rejectsBlankName() throws Exception {
            mockMvc.perform(post("/api/travel/places")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "  "}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("상세가 최신이면 외부를 다시 부르지 않는다")
        void skipsRefreshWhenFresh() throws Exception {
            Long memberId = memberRepository.findAll().get(0).getId();
            TravelPlace saved = placeRepository.save(
                    TravelPlace.fromGoogle(memberId, "ChIJ_senso", "센소지"));
            saved.updateDetails("+81", "{}", null, null, java.time.Instant.now());
            placeRepository.saveAndFlush(saved);

            mockMvc.perform(get("/api/travel/places/" + saved.getId())
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.phone").value("+81"));

            assertThat(stub.detailFetches).isEmpty();
        }

        @Test
        @DisplayName("상세가 30일 지났으면 다시 받아 채운다")
        void refreshesStaleDetails() throws Exception {
            Long memberId = memberRepository.findAll().get(0).getId();
            TravelPlace saved = placeRepository.save(
                    TravelPlace.fromGoogle(memberId, "ChIJ_senso", "센소지"));
            // 상세를 받은 적이 없으면 갱신 대상이다.
            stub.detailResult = Optional.of(place("ChIJ_senso", "센소지"));

            mockMvc.perform(get("/api/travel/places/" + saved.getId())
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.phone").value("+81 3-3842-0181"))
                    .andExpect(jsonPath("$.data.category").value("사찰"));

            assertThat(stub.detailFetches).containsExactly("ChIJ_senso");
        }

        @Test
        @DisplayName("상세를 받으면 사진도 받아 캐시하고 저작자 표기를 함께 준다")
        void cachesPhotoOnRefresh() throws Exception {
            Long memberId = memberRepository.findAll().get(0).getId();
            TravelPlace saved = placeRepository.save(
                    TravelPlace.fromGoogle(memberId, "ChIJ_senso", "센소지"));
            stub.detailResult = Optional.of(place("ChIJ_senso", "센소지"));

            mockMvc.perform(get("/api/travel/places/" + saved.getId())
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.photoUrl")
                            .value(org.hamcrest.Matchers.containsString("travel/places/")))
                    // 표기를 버리면 쓸 수 없는 사진이 된다(구글 약관).
                    .andExpect(jsonPath("$.data.photoAttribution").value("구글 사용자"));

            assertThat(stub.photoFetches).containsExactly("places/p1/photos/ph1");
        }

        @Test
        @DisplayName("이미 받아 둔 사진은 다시 받지 않는다 — 사진 호출도 건당 과금이다")
        void doesNotRefetchPhoto() throws Exception {
            Long memberId = memberRepository.findAll().get(0).getId();
            TravelPlace saved = placeRepository.save(
                    TravelPlace.fromGoogle(memberId, "ChIJ_senso", "센소지"));
            saved.updateDetails(null, null, "travel/places/1/old.jpg", "옛 저작자", null);
            placeRepository.saveAndFlush(saved);
            stub.detailResult = Optional.of(place("ChIJ_senso", "센소지"));

            mockMvc.perform(get("/api/travel/places/" + saved.getId())
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.photoUrl")
                            .value(org.hamcrest.Matchers.endsWith("travel/places/1/old.jpg")));

            // 상세는 갱신됐지만(detailsRefreshedAt이 null이라 대상) 사진은 그대로다.
            assertThat(stub.detailFetches).containsExactly("ChIJ_senso");
            assertThat(stub.photoFetches).isEmpty();
        }

        @Test
        @DisplayName("사진을 못 받아도 장소는 그대로 보인다")
        void survivesPhotoFailure() throws Exception {
            Long memberId = memberRepository.findAll().get(0).getId();
            TravelPlace saved = placeRepository.save(
                    TravelPlace.fromGoogle(memberId, "ChIJ_senso", "센소지"));
            stub.detailResult = Optional.of(place("ChIJ_senso", "센소지"));
            stub.photoResult = Optional.empty();

            mockMvc.perform(get("/api/travel/places/" + saved.getId())
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("센소지"))
                    .andExpect(jsonPath("$.data.phone").value("+81 3-3842-0181"))
                    .andExpect(jsonPath("$.data.photoUrl").doesNotExist());
        }

        @Test
        @DisplayName("남의 장소는 404")
        void otherMembersPlaceIsNotFound() throws Exception {
            Long memberId = memberRepository.findAll().get(0).getId();
            TravelPlace saved = placeRepository.save(
                    TravelPlace.fromGoogle(memberId, "ChIJ_senso", "센소지"));

            mockMvc.perform(get("/api/travel/places/" + saved.getId())
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-008"));
        }
    }

    @Nested
    @DisplayName("일정에 담기")
    class AddToActivity {

        @Test
        @DisplayName("googlePlaceId로 담으면 장소를 저장하고 일정에 연결한다")
        void upsertsPlaceOnCreate() throws Exception {
            long tripId = createTripWithCoordinates();
            stub.detailResult = Optional.of(place("ChIJ_senso", "센소지"));

            mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "센소지", "activityDate": "2026-10-24",
                                     "googlePlaceId": "ChIJ_senso"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.place.name").value("센소지"));

            assertThat(placeRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("같은 장소를 두 번 담아도 장소는 하나만 만든다")
        void reusesExistingPlace() throws Exception {
            long tripId = createTripWithCoordinates();
            stub.detailResult = Optional.of(place("ChIJ_senso", "센소지"));

            for (String title : List.of("아침 센소지", "저녁 센소지")) {
                mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"title": "%s", "activityDate": "2026-10-24",
                                         "googlePlaceId": "ChIJ_senso"}
                                        """.formatted(title)))
                        .andExpect(status().isOk());
            }

            // 여행을 가로질러 같은 장소를 가리켜야 "좋았던 곳" 판정이 성립한다.
            assertThat(placeRepository.findAll()).hasSize(1);
            assertThat(stub.detailFetches).containsExactly("ChIJ_senso");
        }
    }

    /** 편향 검증용 — 목적지 좌표가 있는 여행. */
    private long createTripWithCoordinates() throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "도쿄", "destinationName": "도쿄",
                                 "startDate": "2026-10-24", "endDate": "2026-10-27",
                                 "timezone": "Asia/Tokyo", "currency": "JPY",
                                 "lat": 35.6762, "lng": 139.6503}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.id")).longValue();
    }
}
