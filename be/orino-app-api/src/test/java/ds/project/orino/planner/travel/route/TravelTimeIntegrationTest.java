package ds.project.orino.planner.travel.route;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.repository.TravelPlaceRepository;
import ds.project.orino.planner.travel.route.client.RoutesClient;
import ds.project.orino.planner.travel.route.client.TravelMode;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보드의 이동시간(§4.4). 외부 호출만 스텁으로 갈아끼우고 캐시(Redis)·저장(MySQL)은 실물이다 —
 * 캐시가 정말 호출을 줄이는지는 실제 Redis 없이 확인되지 않는다.
 */
@Import(StubExternalsConfig.class)
class TravelTimeIntegrationTest extends ApiTestSupport {


    /** 센소지. */
    private static final BigDecimal SENSOJI_LAT = new BigDecimal("35.7147651");
    private static final BigDecimal SENSOJI_LNG = new BigDecimal("139.7966553");
    /** 도쿄 스카이트리 — 센소지에서 약 1.3km(도보 판정 안쪽). */
    private static final BigDecimal SKYTREE_LAT = new BigDecimal("35.7100627");
    private static final BigDecimal SKYTREE_LNG = new BigDecimal("139.8107004");
    /** 신주쿠 — 센소지에서 약 8km(자동차 판정). */
    private static final BigDecimal SHINJUKU_LAT = new BigDecimal("35.6895014");
    private static final BigDecimal SHINJUKU_LNG = new BigDecimal("139.6917337");

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TravelPlaceRepository placeRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private RoutesClient routesClient;

    private StubRoutesClient stub;
    private String authHeader;
    private Long memberId;
    private long tripId;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        stub = (StubRoutesClient) routesClient;
        stub.reset();

        memberRepository.save(MemberFixture.create());
        memberId = memberRepository.findAll().get(0).getId();
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        tripId = createTrip();
    }

    /**
     * 좌표를 조금씩 흔들어 테스트마다 다른 캐시 키를 쓴다 — Redis 컨테이너는 테스트 사이에
     * 살아 있어, 앞 테스트가 캐시해 둔 이동시간이 새어 들어오면 호출 횟수 검증이 무너진다.
     */
    private static BigDecimal jitter(BigDecimal base) {
        int nudge = Math.abs(UUID.randomUUID().hashCode() % 9000) + 1000;
        return base.add(new BigDecimal("0.0000001").multiply(BigDecimal.valueOf(nudge)));
    }

    private Long placeAt(String name, BigDecimal lat, BigDecimal lng) {
        TravelPlace place = placeRepository.save(
                TravelPlace.fromGoogle(memberId, "g-" + name + "-" + UUID.randomUUID(), name));
        place.updateBasics(name + " 주소", lat, lng, null, null);
        return placeRepository.saveAndFlush(place).getId();
    }

    /**
     * 도시 식별자를 가진 장소. 도시 경계 판정은 <b>이 값으로만</b> 하고 좌표 거리는 보지
     * 않는다(D-23) — 그래서 아래 테스트들은 좌표를 바꾸지 않고 식별자만 바꿔 경계를 만든다.
     */
    private Long placeIn(String name, String cityPlaceRef, BigDecimal lat, BigDecimal lng) {
        TravelPlace place = placeRepository.findById(placeAt(name, lat, lng)).orElseThrow();
        place.updateCityInfo(cityPlaceRef, cityPlaceRef, "JP");
        return placeRepository.saveAndFlush(place).getId();
    }

    private long createTrip() throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "도쿄",
                                 "startDate": "2026-10-24", "endDate": "2026-10-27",
                                 %s}
                                """.formatted(TravelCityFixture.singleLeg(
                                        cityId("도쿄"), 4))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.id")).longValue();
    }

    private void addActivity(String title, Long placeId) throws Exception {
        String place = placeId == null ? "" : ", \"placeId\": %d".formatted(placeId);
        mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "activityDate": "2026-10-24"%s}
                                """.formatted(title, place)))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions board() throws Exception {
        return mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                        .param("date", "2026-10-24")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }


    private void addUnscheduled(String title, Long placeId) throws Exception {
        String place = placeId == null ? "" : ", \"placeId\": %d".formatted(placeId);
        mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "activityDate": null%s}
                                """.formatted(title, place)))
                .andExpect(status().isOk());
    }

    private List<Integer> activityIds() throws Exception {
        String body = board().andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.data.activities[*].id");
    }

    private List<Integer> archivedActivityIds() throws Exception {
        String body = mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                        .param("archive", "true")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.data.activities[*].id");
    }

    private org.springframework.test.web.servlet.ResultActions reorder(int... ids) throws Exception {
        String list = java.util.Arrays.stream(ids).mapToObj(String::valueOf)
                .collect(java.util.stream.Collectors.joining(", "));
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/travel/trips/" + tripId + "/activities/order")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"moves": [{"date": "2026-10-24", "activityIds": [%s]}]}
                                """.formatted(list)))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions travelTimeBetween(
            int from, int to, String mode) throws Exception {
        return mockMvc.perform(get("/api/travel/trips/" + tripId + "/travel-time")
                .param("from", String.valueOf(from))
                .param("to", String.valueOf(to))
                .param("mode", mode)
                .header(HttpHeaders.AUTHORIZATION, authHeader));
    }

    @Nested
    @DisplayName("어디에 이동시간이 생기나")
    class Which {

        @Test
        @DisplayName("장소가 있는 연속된 두 일정 사이에 생긴다")
        void betweenConsecutivePlaces() throws Exception {
            addActivity("센소지", placeAt("센소지", jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("스카이트리", placeAt("스카이트리", jitter(SKYTREE_LAT), jitter(SKYTREE_LNG)));

            board()
                    .andExpect(jsonPath("$.data.travelTimes", hasSize(1)))
                    .andExpect(jsonPath("$.data.travelTimes[0].durationMinutes").value(12))
                    .andExpect(jsonPath("$.data.travelTimes[0].distanceM").value(900))
                    .andExpect(jsonPath("$.data.travelTimes[0].fallback").value(false));
        }

        @Test
        @DisplayName("사이에 장소 없는 일정이 끼면 건너뛰고 잇는다")
        void skipsActivitiesWithoutPlace() throws Exception {
            addActivity("센소지", placeAt("센소지", jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            // "점심"처럼 장소를 안 정한 일정이 있다고 앞뒤 이동을 모르면 안 된다.
            addActivity("점심", null);
            addActivity("스카이트리", placeAt("스카이트리", jitter(SKYTREE_LAT), jitter(SKYTREE_LNG)));

            board().andExpect(jsonPath("$.data.travelTimes", hasSize(1)));
            assertThat(stub.calls).hasSize(1);
        }

        @Test
        @DisplayName("장소가 하나뿐이면 이동시간이 없다 — 외부를 부르지도 않는다")
        void noTravelTimeWithSinglePlace() throws Exception {
            addActivity("센소지", placeAt("센소지", jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("점심", null);

            board().andExpect(jsonPath("$.data.travelTimes", hasSize(0)));
            assertThat(stub.calls).isEmpty();
        }

        @Test
        @DisplayName("좌표 없는 장소(직접 입력)는 이동시간에 들어가지 않는다")
        void ignoresPlacesWithoutCoordinates() throws Exception {
            TravelPlace manual = placeRepository.save(TravelPlace.manual(memberId, "골목 카페"));
            addActivity("센소지", placeAt("센소지", jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("골목 카페", manual.getId());

            board().andExpect(jsonPath("$.data.travelTimes", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("이동수단은 직선거리로 정한다(§1.3)")
    class Mode {

        @Test
        @DisplayName("1.5km 이하는 도보")
        void walkWhenClose() throws Exception {
            addActivity("센소지", placeAt("센소지", jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("스카이트리", placeAt("스카이트리", jitter(SKYTREE_LAT), jitter(SKYTREE_LNG)));

            board().andExpect(jsonPath("$.data.travelTimes[0].mode").value("WALK"));
            assertThat(stub.calls.get(0).mode()).isEqualTo(TravelMode.WALK);
        }

        @Test
        @DisplayName("1.5km 초과는 자동차")
        void driveWhenFar() throws Exception {
            addActivity("센소지", placeAt("센소지", jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("신주쿠", placeAt("신주쿠", jitter(SHINJUKU_LAT), jitter(SHINJUKU_LNG)));

            board().andExpect(jsonPath("$.data.travelTimes[0].mode").value("DRIVE"));
            assertThat(stub.calls.get(0).mode()).isEqualTo(TravelMode.DRIVE);
        }
    }

    @Nested
    @DisplayName("Routes가 막혔을 때")
    class Fallback {

        @Test
        @DisplayName("이동시간 행은 남기고 직선거리로 대체한다 — 거리만이라도 알면 계획이 선다")
        void fallsBackToStraightLine() throws Exception {
            stub.result = Optional.empty();
            addActivity("센소지", placeAt("센소지", jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("신주쿠", placeAt("신주쿠", jitter(SHINJUKU_LAT), jitter(SHINJUKU_LNG)));

            board()
                    .andExpect(jsonPath("$.data.travelTimes", hasSize(1)))
                    .andExpect(jsonPath("$.data.travelTimes[0].fallback").value(true))
                    .andExpect(jsonPath("$.data.travelTimes[0].durationMinutes").doesNotExist())
                    // 수단은 여전히 정해진다(직선거리로 판정하므로).
                    .andExpect(jsonPath("$.data.travelTimes[0].mode").value("DRIVE"))
                    .andExpect(jsonPath("$.data.travelTimes[0].distanceM").isNumber());
        }

        @Test
        @DisplayName("실패는 캐시하지 않는다 — 복구된 뒤에도 계속 fallback이면 안 된다")
        void doesNotCacheFailure() throws Exception {
            Long from = placeAt("센소지", jitter(SENSOJI_LAT), jitter(SENSOJI_LNG));
            Long to = placeAt("스카이트리", jitter(SKYTREE_LAT), jitter(SKYTREE_LNG));
            addActivity("센소지", from);
            addActivity("스카이트리", to);

            stub.result = Optional.empty();
            board().andExpect(jsonPath("$.data.travelTimes[0].fallback").value(true));

            stub.result = Optional.of(new RoutesClient.Route(720, 900));
            board().andExpect(jsonPath("$.data.travelTimes[0].fallback").value(false));

            assertThat(stub.calls).hasSize(2);
        }
    }

    @Nested
    @DisplayName("캐시")
    class Caching {

        @Test
        @DisplayName("보드를 다시 열어도 외부 호출이 늘지 않는다 — 탭을 넘길 때마다 과금될 순 없다")
        void reusesCachedTravelTime() throws Exception {
            addActivity("센소지", placeAt("센소지", jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("스카이트리", placeAt("스카이트리", jitter(SKYTREE_LAT), jitter(SKYTREE_LNG)));

            for (int i = 0; i < 3; i++) {
                board().andExpect(jsonPath("$.data.travelTimes[0].durationMinutes").value(12));
            }

            assertThat(stub.calls).hasSize(1);
        }

        @Test
        @DisplayName("순서를 뒤집으면 다른 이동이라 새로 부른다 — 일방통행이면 경로가 다르다")
        void reversedOrderIsDifferentTravelTime() throws Exception {
            BigDecimal aLat = jitter(SENSOJI_LAT);
            BigDecimal aLng = jitter(SENSOJI_LNG);
            addActivity("센소지", placeAt("센소지", aLat, aLng));
            addActivity("스카이트리", placeAt("스카이트리",
                    jitter(SKYTREE_LAT), jitter(SKYTREE_LNG)));

            String body = board().andReturn().getResponse().getContentAsString();
            List<Integer> ids = com.jayway.jsonpath.JsonPath.read(body, "$.data.activities[*].id");
            assertThat(stub.calls).hasSize(1);
            assertThat(stub.calls.get(0).origin().lat()).isEqualByComparingTo(aLat);

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .put("/api/travel/trips/" + tripId + "/activities/order")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"moves": [{"date": "2026-10-24",
                                                "activityIds": [%d, %d]}]}
                                    """.formatted(ids.get(1), ids.get(0))))
                    .andExpect(status().isOk());
            board();

            // 캐시 키에 방향이 들어 있어야 반대 방향 이동을 따로 잡는다.
            assertThat(stub.calls).hasSize(2);
            assertThat(stub.calls.get(1).destination().lat()).isEqualByComparingTo(aLat);
        }
    }

    @Nested
    @DisplayName("여러 이동시간")
    class Multiple {

        @Test
        @DisplayName("일정이 셋이면 이동시간은 둘 — 리스트 사이사이에 들어간다")
        void chainsAcrossActivities() throws Exception {
            addActivity("센소지", placeAt("센소지", jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("스카이트리", placeAt("스카이트리", jitter(SKYTREE_LAT), jitter(SKYTREE_LNG)));
            addActivity("신주쿠", placeAt("신주쿠", jitter(SHINJUKU_LAT), jitter(SHINJUKU_LNG)));

            String body = board()
                    .andExpect(jsonPath("$.data.travelTimes", hasSize(2)))
                    .andReturn().getResponse().getContentAsString();

            List<Integer> froms = com.jayway.jsonpath.JsonPath.read(body, "$.data.travelTimes[*].fromActivityId");
            List<Integer> tos = com.jayway.jsonpath.JsonPath.read(body, "$.data.travelTimes[*].toActivityId");
            // 앞 이동의 도착이 뒤 이동의 출발이다.
            assertThat(tos.get(0)).isEqualTo(froms.get(1));
        }
    }

    @Nested
    @DisplayName("보관함")
    class Archive {

        @Test
        @DisplayName("보관함에는 이동시간이 없다 — 순서에 이동 의미가 없는데 유료 호출을 낼 이유가 없다")
        void archiveHasNoTravelTimes() throws Exception {
            Long a = placeAt("센소지", jitter(SENSOJI_LAT), jitter(SENSOJI_LNG));
            Long b = placeAt("스카이트리", jitter(SKYTREE_LAT), jitter(SKYTREE_LNG));
            addUnscheduled("센소지", a);
            addUnscheduled("스카이트리", b);

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                            .param("archive", "true")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.activities", hasSize(2)))
                    .andExpect(jsonPath("$.data.travelTimes", hasSize(0)));

            assertThat(stub.calls).isEmpty();
        }
    }

    /**
     * 목적은 비용이 아니라 <b>오답 제거</b>다 — 오사카에서 도쿄에 "자동차 6시간"이 뜨면 그
     * 화면은 신뢰를 잃는다. 호출이 주는 것은 부수 효과다.
     */
    @Nested
    @DisplayName("도시 경계(§3.4)")
    class CityBoundary {

        @Test
        @DisplayName("같은 도시 안 두 장소는 v2.0 그대로 계산한다")
        void computesWithinSameCity() throws Exception {
            addActivity("센소지", placeIn("센소지", "ChIJ_tokyo",
                    jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("스카이트리", placeIn("스카이트리", "ChIJ_tokyo",
                    jitter(SKYTREE_LAT), jitter(SKYTREE_LNG)));

            board()
                    .andExpect(jsonPath("$.data.travelTimes", hasSize(1)))
                    .andExpect(jsonPath("$.data.travelTimes[0].crossCity").value(false))
                    .andExpect(jsonPath("$.data.travelTimes[0].mode").value("WALK"))
                    .andExpect(jsonPath("$.data.travelTimes[0].durationMinutes").value(12));
            assertThat(stub.calls).hasSize(1);
        }

        @Test
        @DisplayName("도시를 넘으면 계산하지 않는다 — 외부 호출이 0회다")
        void skipsAcrossCities() throws Exception {
            // 좌표는 둘 다 도쿄 안이다. 그래도 도시 식별자가 다르면 경계다 —
            // 판정이 좌표 거리로 새면 이 테스트가 통과하지 않는다.
            addActivity("오사카 가게", placeIn("오사카 가게", "ChIJ_osaka",
                    jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("교토 가게", placeIn("교토 가게", "ChIJ_kyoto",
                    jitter(SKYTREE_LAT), jitter(SKYTREE_LNG)));

            board()
                    // 행 자체는 남는다 — 사라지면 "이동이 없다"로 읽힌다.
                    .andExpect(jsonPath("$.data.travelTimes", hasSize(1)))
                    .andExpect(jsonPath("$.data.travelTimes[0].crossCity").value(true))
                    .andExpect(jsonPath("$.data.travelTimes[0].mode").doesNotExist())
                    .andExpect(jsonPath("$.data.travelTimes[0].durationMinutes").doesNotExist())
                    .andExpect(jsonPath("$.data.travelTimes[0].fallback").value(false));

            assertThat(stub.calls).isEmpty();
        }

        @Test
        @DisplayName("도시 식별자를 모르는 장소는 경계로 판정하지 않는다 — 모르는 것을 경고로 바꾸지 않는다")
        void unknownCityIsNotABoundary() throws Exception {
            addActivity("교토 가게", placeIn("교토 가게", "ChIJ_kyoto",
                    jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            // 직접 입력한 장소처럼 도시를 모르는 경우.
            addActivity("골목 카페", placeAt("골목 카페",
                    jitter(SKYTREE_LAT), jitter(SKYTREE_LNG)));

            board()
                    .andExpect(jsonPath("$.data.travelTimes[0].crossCity").value(false))
                    .andExpect(jsonPath("$.data.travelTimes[0].durationMinutes").value(12));
            assertThat(stub.calls).hasSize(1);
        }

        @Test
        @DisplayName("이동수단 시트도 도시를 넘으면 계산하지 않는다 — 보드와 답이 갈리면 안 된다")
        void sheetAlsoRefusesAcrossCities() throws Exception {
            addActivity("오사카 가게", placeIn("오사카 가게", "ChIJ_osaka",
                    jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("교토 가게", placeIn("교토 가게", "ChIJ_kyoto",
                    jitter(SKYTREE_LAT), jitter(SKYTREE_LNG)));
            List<Integer> ids = activityIds();

            travelTimeBetween(ids.get(0), ids.get(1), "WALK")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.crossCity").value(true))
                    .andExpect(jsonPath("$.data.durationMinutes").doesNotExist());

            assertThat(stub.calls).isEmpty();
        }
    }

    @Nested
    @DisplayName("출발 알림을 켤 수 있는가")
    class DepartureNotifiable {

        @Test
        @DisplayName("도시를 넘어 들어오는 일정은 켤 수 없다 — 언제 나서야 할지 모른다")
        void blockedWhenIncomingCrossesCity() throws Exception {
            addActivity("오사카 가게", placeIn("오사카 가게", "ChIJ_osaka",
                    jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("교토 가게", placeIn("교토 가게", "ChIJ_kyoto",
                    jitter(SKYTREE_LAT), jitter(SKYTREE_LNG)));

            board()
                    .andExpect(jsonPath("$.data.activities[1].canDepartureNotify").value(false));
        }

        @Test
        @DisplayName("같은 도시 안이면 켤 수 있다")
        void allowedWithinSameCity() throws Exception {
            addActivity("센소지", placeIn("센소지", "ChIJ_tokyo",
                    jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("스카이트리", placeIn("스카이트리", "ChIJ_tokyo",
                    jitter(SKYTREE_LAT), jitter(SKYTREE_LNG)));

            board()
                    .andExpect(jsonPath("$.data.activities[1].canDepartureNotify").value(true));
        }

        @Test
        @DisplayName("그날 첫 일정은 켤 수 없다 — 어디서 출발하는지가 없다")
        void blockedForFirstActivity() throws Exception {
            addActivity("센소지", placeIn("센소지", "ChIJ_tokyo",
                    jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("스카이트리", placeIn("스카이트리", "ChIJ_tokyo",
                    jitter(SKYTREE_LAT), jitter(SKYTREE_LNG)));

            board()
                    .andExpect(jsonPath("$.data.activities[0].canDepartureNotify").value(false));
        }

        @Test
        @DisplayName("상세 응답도 보드와 같은 답을 준다 — 스위치가 있는 화면이 상세다")
        void detailAgreesWithBoard() throws Exception {
            addActivity("오사카 가게", placeIn("오사카 가게", "ChIJ_osaka",
                    jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("교토 가게", placeIn("교토 가게", "ChIJ_kyoto",
                    jitter(SKYTREE_LAT), jitter(SKYTREE_LNG)));
            List<Integer> ids = activityIds();

            mockMvc.perform(get("/api/travel/activities/" + ids.get(1))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.canDepartureNotify").value(false));

            // 판정에 외부 호출이 끼면 일정을 열 때마다 과금된다.
            assertThat(stub.calls).isEmpty();
        }

        @Test
        @DisplayName("보관함 일정은 켤 수 없다 — 날짜가 없어 알림 시각 자체가 서지 않는다")
        void blockedInArchive() throws Exception {
            addUnscheduled("센소지", placeIn("센소지", "ChIJ_tokyo",
                    jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addUnscheduled("스카이트리", placeIn("스카이트리", "ChIJ_tokyo",
                    jitter(SKYTREE_LAT), jitter(SKYTREE_LNG)));

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                            .param("archive", "true")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.activities[1].canDepartureNotify").value(false));
        }
    }

    @Nested
    @DisplayName("순서 변경 응답")
    class Reorder {

        @Test
        @DisplayName("재계산된 이동시간을 함께 돌려준다 — 드래그는 손을 뗀 순간 결과가 보여야 한다")
        void returnsRecomputedTravelTimes() throws Exception {
            addActivity("센소지", placeAt("센소지", jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("스카이트리", placeAt("스카이트리", jitter(SKYTREE_LAT), jitter(SKYTREE_LNG)));
            List<Integer> ids = activityIds();

            reorder(ids.get(1), ids.get(0))
                    .andExpect(jsonPath("$.data.travelTimes", hasSize(1)))
                    // 뒤집었으니 출발·도착도 뒤집혀야 한다.
                    .andExpect(jsonPath("$.data.travelTimes[0].fromActivityId").value(ids.get(1)))
                    .andExpect(jsonPath("$.data.travelTimes[0].toActivityId").value(ids.get(0)));
        }

        @Test
        @DisplayName("이동시간이 없어도 빈 배열로 온다")
        void emptyWhenNoTravelTimes() throws Exception {
            addActivity("점심", null);
            addActivity("저녁", null);
            List<Integer> ids = activityIds();

            reorder(ids.get(1), ids.get(0))
                    .andExpect(jsonPath("$.data.travelTimes", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("수단별 단건 조회 (이동수단 시트)")
    class ByMode {

        @Test
        @DisplayName("자동 판정과 다른 수단도 물어볼 수 있다")
        void asksForTheOtherMode() throws Exception {
            addActivity("센소지", placeAt("센소지", jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("신주쿠", placeAt("신주쿠", jitter(SHINJUKU_LAT), jitter(SHINJUKU_LNG)));
            List<Integer> ids = activityIds();

            // 8km라 보드는 DRIVE로 준다. 시트에서 도보를 물으면 그때 계산한다.
            board().andExpect(jsonPath("$.data.travelTimes[0].mode").value("DRIVE"));
            travelTimeBetween(ids.get(0), ids.get(1), "WALK")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.mode").value("WALK"))
                    .andExpect(jsonPath("$.data.durationMinutes").value(12));

            // 보드에서 미리 둘 다 계산하지 않는다 — 아무도 안 열어 볼 값까지 사게 된다.
            assertThat(stub.calls).hasSize(2);
            assertThat(stub.calls.get(0).mode()).isEqualTo(TravelMode.DRIVE);
            assertThat(stub.calls.get(1).mode()).isEqualTo(TravelMode.WALK);
        }

        @Test
        @DisplayName("보드와 같은 캐시를 탄다 — 시트를 다시 열어도 외부 호출이 없다")
        void sharesCacheWithBoard() throws Exception {
            addActivity("센소지", placeAt("센소지", jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("스카이트리", placeAt("스카이트리", jitter(SKYTREE_LAT), jitter(SKYTREE_LNG)));
            List<Integer> ids = activityIds();

            board();
            int afterBoard = stub.calls.size();
            // 보드가 이미 WALK로 계산해 둔 이동이다.
            travelTimeBetween(ids.get(0), ids.get(1), "WALK").andExpect(status().isOk());
            travelTimeBetween(ids.get(0), ids.get(1), "WALK").andExpect(status().isOk());

            assertThat(stub.calls).hasSize(afterBoard);
        }

        @Test
        @DisplayName("좌표 없는 일정 사이는 400 — 화면에도 그 이동시간 행이 없다")
        void rejectsWhenNoCoordinates() throws Exception {
            addActivity("센소지", placeAt("센소지", jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("점심", null);
            List<Integer> ids = activityIds();

            travelTimeBetween(ids.get(0), ids.get(1), "WALK")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-009"));
        }

        @Test
        @DisplayName("보관함 일정은 400 — 이동 의미가 없다")
        void rejectsArchivedActivities() throws Exception {
            Long a = placeAt("센소지", jitter(SENSOJI_LAT), jitter(SENSOJI_LNG));
            Long b = placeAt("스카이트리", jitter(SKYTREE_LAT), jitter(SKYTREE_LNG));
            addUnscheduled("센소지", a);
            addUnscheduled("스카이트리", b);
            List<Integer> ids = archivedActivityIds();

            travelTimeBetween(ids.get(0), ids.get(1), "WALK")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-009"));
        }

        @Test
        @DisplayName("남의 여행은 404")
        void rejectsOtherMembersTrip() throws Exception {
            addActivity("센소지", placeAt("센소지", jitter(SENSOJI_LAT), jitter(SENSOJI_LNG)));
            addActivity("스카이트리", placeAt("스카이트리", jitter(SKYTREE_LAT), jitter(SKYTREE_LNG)));
            List<Integer> ids = activityIds();

            memberRepository.save(MemberFixture.create("other", "password"));
            String otherAuth = "Bearer "
                    + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/travel-time")
                            .param("from", String.valueOf(ids.get(0)))
                            .param("to", String.valueOf(ids.get(1)))
                            .param("mode", "WALK")
                            .header(HttpHeaders.AUTHORIZATION, otherAuth))
                    .andExpect(status().isNotFound());
        }
    }


    /** 여행에는 기준 도시가 있어야 한다(v2.1). 같은 이름이어도 매번 새로 만든다. */
    private long cityId(String name) throws Exception {
        return TravelCityFixture.createCity(mockMvc, authHeader, name, "Asia/Tokyo", "JPY");
    }

}
