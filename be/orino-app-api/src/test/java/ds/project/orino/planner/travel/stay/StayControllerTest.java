package ds.project.orino.planner.travel.stay;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.TripStay;
import ds.project.orino.domain.planner.travel.repository.TravelPlaceRepository;
import ds.project.orino.domain.planner.travel.repository.TripStayRepository;
import ds.project.orino.planner.travel.place.StubPlacesClient;
import ds.project.orino.planner.travel.place.client.PlaceResult;
import ds.project.orino.planner.travel.place.client.PlacesClient;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 숙소 CRUD와 보드에 붙는 숙소(§4.5 · §3.5).
 *
 * <p>이 테스트의 절반은 <b>겹침 경계</b>다. 체크아웃일과 다음 체크인일이 같은 것은 겹침이
 * 아니라 이동일의 정상 모양인데, 반열린 구간을 닫힌 구간으로 잘못 다루면 연박 일정을 아예
 * 만들 수 없게 된다.
 */
// 스텁 조합을 새로 만들지 않는다 — 조합이 늘 때마다 Spring 컨텍스트가 하나씩 더 뜨고,
// 각각이 커넥션 풀을 물고 있어 전체 실행에서 OutOfMemoryError로 무너진다.
@Import(StubExternalsConfig.class)
class StayControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TravelPlaceRepository placeRepository;
    @Autowired
    private TripStayRepository stayRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private PlacesClient placesClient;

    private StubPlacesClient placesStub;
    private String authHeader;
    private String otherAuthHeader;
    private long tripId;
    private long osaka;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        placesStub = (StubPlacesClient) placesClient;
        placesStub.reset();
        memberRepository.save(MemberFixture.create());
        memberRepository.save(MemberFixture.create("other", "password"));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        otherAuthHeader = "Bearer "
                + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");

        osaka = TravelCityFixture.createCity(mockMvc, authHeader, "오사카", "Asia/Tokyo", "JPY");
        tripId = createTrip();
    }

    @Nested
    @DisplayName("등록 · 목록")
    class Create {

        @Test
        @DisplayName("숙소를 등록하면 묵는 밤 수와 함께 돌아온다")
        void createsStay() throws Exception {
            createStay("도톤보리 호텔", "2026-10-24", "2026-10-27")
                    .andExpect(jsonPath("$.data.name").value("도톤보리 호텔"))
                    .andExpect(jsonPath("$.data.checkInDate").value("2026-10-24"))
                    .andExpect(jsonPath("$.data.checkOutDate").value("2026-10-27"))
                    // [in, out) 반열린 구간이라 24·25·26 세 밤이다.
                    .andExpect(jsonPath("$.data.nights").value(3));

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/stays")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)));
        }

        @Test
        @DisplayName("체크아웃일과 다음 체크인일이 같은 것은 겹침이 아니다 — 이동일의 정상 모양")
        void allowsBackToBackStays() throws Exception {
            createStay("오사카 호텔", "2026-10-24", "2026-10-26");

            createStay("교토 게스트하우스", "2026-10-26", "2026-10-27")
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("묵는 밤이 겹치면 409 — 어느 숙소와 겹치는지 함께 준다")
        void rejectsOverlap() throws Exception {
            String body = createStay("오사카 호텔", "2026-10-24", "2026-10-27")
                    .andReturn().getResponse().getContentAsString();
            long existing = ((Number) JsonPath.read(body, "$.data.stayId")).longValue();

            mockMvc.perform(post("/api/travel/trips/" + tripId + "/stays")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(stayBody("교토 호텔", "2026-10-26", "2026-10-27")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-017"))
                    // "겹칩니다"만 말하면 사용자가 할 수 있는 일이 없다.
                    .andExpect(jsonPath("$.data.stayId").value((int) existing))
                    .andExpect(jsonPath("$.data.name").value("오사카 호텔"))
                    .andExpect(jsonPath("$.data.checkOutDate").value("2026-10-27"));
        }

        @Test
        @DisplayName("체크아웃이 체크인보다 뒤가 아니면 400 — 0박은 어느 날짜에도 안 붙는다")
        void rejectsEmptyPeriod() throws Exception {
            mockMvc.perform(post("/api/travel/trips/" + tripId + "/stays")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(stayBody("하루도 안 묵는 곳", "2026-10-25", "2026-10-25")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-002"));
        }

        @Test
        @DisplayName("여행 기간 밖이면 400 — 붙을 날짜가 없다")
        void rejectsOutOfPeriod() throws Exception {
            mockMvc.perform(post("/api/travel/trips/" + tripId + "/stays")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(stayBody("기간 밖", "2026-11-01", "2026-11-03")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-007"));
        }

        @Test
        @DisplayName("남의 여행에는 숙소를 넣을 수 없다")
        void scopedByMember() throws Exception {
            mockMvc.perform(post("/api/travel/trips/" + tripId + "/stays")
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(stayBody("남의 숙소", "2026-10-24", "2026-10-26")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-001"));
        }
    }

    @Nested
    @DisplayName("수정 · 삭제")
    class UpdateAndDelete {

        @Test
        @DisplayName("기간을 그대로 두고 이름만 고칠 수 있다 — 자기 자신과는 겹치지 않는다")
        void updatesWithoutSelfOverlap() throws Exception {
            long stayId = stayId(createStay("도톤보리 호텔", "2026-10-24", "2026-10-27"));

            mockMvc.perform(put("/api/travel/stays/" + stayId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(stayBody("난바 호텔", "2026-10-24", "2026-10-27")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("난바 호텔"));
        }

        @Test
        @DisplayName("다른 숙소 기간으로 밀면 409")
        void rejectsOverlapOnUpdate() throws Exception {
            createStay("오사카 호텔", "2026-10-24", "2026-10-26");
            long stayId = stayId(createStay("교토 호텔", "2026-10-26", "2026-10-27"));

            mockMvc.perform(put("/api/travel/stays/" + stayId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(stayBody("교토 호텔", "2026-10-25", "2026-10-27")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-017"));
        }

        @Test
        @DisplayName("삭제하면 목록에서 사라진다")
        void deletesStay() throws Exception {
            long stayId = stayId(createStay("도톤보리 호텔", "2026-10-24", "2026-10-27"));

            mockMvc.perform(delete("/api/travel/stays/" + stayId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/stays")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }

        @Test
        @DisplayName("검색 결과를 그대로 담으면 장소를 만들어 붙인다")
        void upsertsPlaceFromGoogle() throws Exception {
            placesStub.detailResult = Optional.of(hotelResult());

            long stayId = stayId(mockMvc.perform(post("/api/travel/trips/" + tripId + "/stays")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "난바 호텔", "googlePlaceId": "ChIJ_namba",
                                     "checkInDate": "2026-10-24", "checkOutDate": "2026-10-26"}
                                    """))
                    .andExpect(status().isOk()));

            TripStay stay = stayRepository.findById(stayId).orElseThrow();
            assertThat(stay.getPlaceId()).isNotNull();
            // 좌표가 붙어야 숙소 이동 시간이 계산된다.
            assertThat(placeRepository.findById(stay.getPlaceId()).orElseThrow().getLat())
                    .isNotNull();
        }

        @Test
        @DisplayName("어느 도시인지 함께 주면 도시 식별자까지 채운다 — 경계 판정이 여기서 산다")
        void savesCityIdentifier() throws Exception {
            placesStub.detailResult = Optional.of(hotelResult());
            withCityRef(osaka, "ChIJ_osaka");

            long stayId = stayId(mockMvc.perform(post("/api/travel/trips/" + tripId + "/stays")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "난바 호텔", "googlePlaceId": "ChIJ_namba",
                                     "cityPlaceId": %d,
                                     "checkInDate": "2026-10-24", "checkOutDate": "2026-10-26"}
                                    """.formatted(osaka)))
                    .andExpect(status().isOk()));

            TripStay stay = stayRepository.findById(stayId).orElseThrow();
            assertThat(placeRepository.findById(stay.getPlaceId()).orElseThrow()
                    .getCityPlaceRef()).isEqualTo("ChIJ_osaka");
        }

        @Test
        @DisplayName("도시를 안 주면 식별자도 없다 — 좌표로 도시를 추측하지 않는다(D-23)")
        void leavesCityIdentifierEmptyWithoutCity() throws Exception {
            placesStub.detailResult = Optional.of(hotelResult());

            long stayId = stayId(mockMvc.perform(post("/api/travel/trips/" + tripId + "/stays")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "난바 호텔", "googlePlaceId": "ChIJ_namba",
                                     "checkInDate": "2026-10-24", "checkOutDate": "2026-10-26"}
                                    """))
                    .andExpect(status().isOk()));

            TripStay stay = stayRepository.findById(stayId).orElseThrow();
            assertThat(placeRepository.findById(stay.getPlaceId()).orElseThrow()
                    .getCityPlaceRef()).isNull();
        }

        @Test
        @DisplayName("숙소를 지워도 `일정으로 추가`로 만든 일정은 남는다")
        void keepsActivitiesMadeFromStay() throws Exception {
            long stayId = stayId(createStay("도톤보리 호텔", "2026-10-24", "2026-10-27"));
            // 시트의 `일정으로 추가`가 만드는 것 — 만들고 나면 숙소와 아무 관계가 없는
            // 보통 일정이다. 숙소를 지웠다고 사라지면 사용자가 옮겨 둔 순서·메모까지 날아간다.
            createActivityWithoutPlace("도톤보리 호텔 체크인", "2026-10-24");

            mockMvc.perform(delete("/api/travel/stays/" + stayId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                            .param("date", "2026-10-24")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.activities", hasSize(1)))
                    .andExpect(jsonPath("$.data.activities[0].title")
                            .value("도톤보리 호텔 체크인"));
        }

        @Test
        @DisplayName("남의 숙소는 404 — 존재 여부가 새어나가지 않는다")
        void deleteScopedByMember() throws Exception {
            long stayId = stayId(createStay("도톤보리 호텔", "2026-10-24", "2026-10-27"));

            mockMvc.perform(delete("/api/travel/stays/" + stayId)
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-020"));
        }
    }

    @Nested
    @DisplayName("보드에 붙는 숙소")
    class OnBoard {

        @Test
        @DisplayName("묵는 밤에는 오늘 밤 숙소가, 체크아웃일에는 체크아웃이 붙는다")
        void fillsDayFields() throws Exception {
            createStayWithTimes("도톤보리 호텔", "2026-10-24", "2026-10-26", "15:00", "11:00");

            board("2026-10-24")
                    .andExpect(jsonPath("$.data.days[0].stayTonight.name")
                            .value("도톤보리 호텔"))
                    .andExpect(jsonPath("$.data.days[0].stayTonight.isCheckInDay").value(true))
                    .andExpect(jsonPath("$.data.days[0].stayTonight.checkInTime").value("15:00"))
                    // 체크아웃일 밤은 이미 다른 곳에서 잔다 — 26일에는 오늘 밤 숙소가 없다.
                    .andExpect(jsonPath("$.data.days[2].stayTonight").doesNotExist())
                    .andExpect(jsonPath("$.data.days[2].stayCheckout.name")
                            .value("도톤보리 호텔"))
                    .andExpect(jsonPath("$.data.days[2].stayCheckout.checkOutTime")
                            .value("11:00"));
        }

        @Test
        @DisplayName("마지막 일정에서 숙소까지 이동 행이 붙는다 — 아직 안 적었으면 빈 행으로")
        void addsStayMoveRow() throws Exception {
            long stayPlace = poiInCity("난바 호텔", "ChIJ_osaka");
            withCityRef(osaka, "ChIJ_osaka");
            createActivity("구로몬 시장", "2026-10-24", poiInCity("구로몬 시장", "ChIJ_osaka"));
            createStayWithPlace("난바 호텔", "2026-10-24", "2026-10-26", stayPlace);

            board("2026-10-24")
                    .andExpect(jsonPath("$.data.stayMove.toStayId").isNumber())
                    .andExpect(jsonPath("$.data.stayMove.mode").doesNotExist());
        }

        @Test
        @DisplayName("숙소로 가는 이동도 적을 수 있다 — 일정 사이 이동과 같은 저장소다 (#1208)")
        void savesStayMove() throws Exception {
            withCityRef(osaka, "ChIJ_osaka");
            long stayPlace = poiInCity("난바 호텔", "ChIJ_osaka");
            long activityId = createActivity("구로몬 시장", "2026-10-24",
                    poiInCity("구로몬 시장", "ChIJ_osaka"));
            long stayId = createStayWithPlace("난바 호텔", "2026-10-24", "2026-10-26", stayPlace);

            mockMvc.perform(put("/api/travel/trips/" + tripId + "/moves")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fromActivityId": %d, "toStayId": %d,
                                     "mode": "SUBWAY", "name": "미도스지선",
                                     "durationMinutes": 14}
                                    """.formatted(activityId, stayId)))
                    .andExpect(status().isOk());

            board("2026-10-24")
                    .andExpect(jsonPath("$.data.stayMove.mode").value("SUBWAY"))
                    .andExpect(jsonPath("$.data.stayMove.name").value("미도스지선"))
                    .andExpect(jsonPath("$.data.stayMove.durationMinutes").value(14));
        }

        @Test
        @DisplayName("도시가 달라도 적을 수 있다 — 교토 숙소로 돌아가는 밤이 그렇다")
        void allowsStayMoveAcrossCities() throws Exception {
            withCityRef(osaka, "ChIJ_osaka");
            long stayPlace = poiInCity("교토 게스트하우스", "ChIJ_kyoto");
            long activityId = createActivity("구로몬 시장", "2026-10-24",
                    poiInCity("구로몬 시장", "ChIJ_osaka"));
            long stayId = createStayWithPlace("교토 게스트하우스", "2026-10-24", "2026-10-26",
                    stayPlace);

            mockMvc.perform(put("/api/travel/trips/" + tripId + "/moves")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fromActivityId": %d, "toStayId": %d,
                                     "mode": "TRAIN", "durationMinutes": 55}
                                    """.formatted(activityId, stayId)))
                    .andExpect(status().isOk());

            board("2026-10-24")
                    .andExpect(jsonPath("$.data.stayMove.durationMinutes").value(55));
        }

        @Test
        @DisplayName("마지막 일정이 이미 그 숙소면 이동 행을 만들지 않는다")
        void noMoveWhenAlreadyAtStay() throws Exception {
            withCityRef(osaka, "ChIJ_osaka");
            long hotel = poiInCity("난바 호텔", "ChIJ_osaka");
            // `일정으로 추가`가 만드는 체크인 일정 — 그 장소가 곧 숙소다.
            createActivity("난바 호텔 체크인", "2026-10-24", hotel);
            createStayWithPlace("난바 호텔", "2026-10-24", "2026-10-26", hotel);

            board("2026-10-24")
                    .andExpect(jsonPath("$.data.stayMove").doesNotExist());
        }

        @Test
        @DisplayName("장소 없는 일정이 뒤에 끼어도 판정이 밀리지 않는다")
        void looksAtLastLocatedActivity() throws Exception {
            withCityRef(osaka, "ChIJ_osaka");
            long hotel = poiInCity("난바 호텔", "ChIJ_osaka");
            createActivity("난바 호텔 체크인", "2026-10-24", hotel);
            // 장소가 없어 이동 판정에서 건너뛰는 일정이다.
            createActivityWithoutPlace("짐 정리", "2026-10-24");
            createStayWithPlace("난바 호텔", "2026-10-24", "2026-10-26", hotel);

            board("2026-10-24")
                    .andExpect(jsonPath("$.data.stayMove").doesNotExist());
        }

        @Test
        @DisplayName("숙소에 장소가 없으면 이동 행이 없다 — 도착지가 없어 적을 수도 없다")
        void noStayMoveWithoutStayPlace() throws Exception {
            withCityRef(osaka, "ChIJ_osaka");
            createActivity("구로몬 시장", "2026-10-24", poiInCity("구로몬 시장", "ChIJ_osaka"));
            createStay("이름만 적은 숙소", "2026-10-24", "2026-10-26");

            board("2026-10-24")
                    .andExpect(jsonPath("$.data.stayMove").doesNotExist());
        }

        @Test
        @DisplayName("보관함에는 그날 밤이 없다 — 숙소 이동도 없다")
        void noStayMoveInArchive() throws Exception {
            createStay("도톤보리 호텔", "2026-10-24", "2026-10-26");

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                            .param("archive", "true")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.stayMove").doesNotExist());
        }
    }

    // ---------------- helpers ----------------

    private long createTrip() throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "오사카", "startDate": "2026-10-24",
                                 "endDate": "2026-10-27", %s}
                                """.formatted(TravelCityFixture.singleLeg(osaka, 4))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private static String stayBody(String name, String checkIn, String checkOut) {
        return """
                {"name": "%s", "checkInDate": "%s", "checkOutDate": "%s"}
                """.formatted(name, checkIn, checkOut);
    }

    private org.springframework.test.web.servlet.ResultActions createStay(
            String name, String checkIn, String checkOut) throws Exception {
        return mockMvc.perform(post("/api/travel/trips/" + tripId + "/stays")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stayBody(name, checkIn, checkOut)))
                .andExpect(status().isOk());
    }

    private void createStayWithTimes(String name, String checkIn, String checkOut,
                                     String inTime, String outTime) throws Exception {
        mockMvc.perform(post("/api/travel/trips/" + tripId + "/stays")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "checkInDate": "%s", "checkOutDate": "%s",
                                 "checkInTime": "%s", "checkOutTime": "%s"}
                                """.formatted(name, checkIn, checkOut, inTime, outTime)))
                .andExpect(status().isOk());
    }

    private long createStayWithPlace(String name, String checkIn, String checkOut, long placeId)
            throws Exception {
        return stayId(mockMvc.perform(post("/api/travel/trips/" + tripId + "/stays")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "checkInDate": "%s", "checkOutDate": "%s",
                                 "placeId": %d}
                                """.formatted(name, checkIn, checkOut, placeId)))
                .andExpect(status().isOk()));
    }

    /** 검색으로 고른 호텔. 좌표가 있어야 숙소 이동 시간이 계산된다. */
    private static PlaceResult hotelResult() {
        return new PlaceResult("ChIJ_namba", "난바 호텔", "오사카시 주오구",
                new BigDecimal("34.6656"), new BigDecimal("135.5061"), "호텔",
                new BigDecimal("4.2"), null, null, "Asia/Tokyo", "오사카", "JP",
                List.of("lodging"));
    }

    private static long stayId(org.springframework.test.web.servlet.ResultActions result)
            throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(),
                "$.data.stayId")).longValue();
    }

    private long createActivity(String title, String date, long placeId) throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "activityDate": "%s", "placeId": %d}
                                """.formatted(title, date, placeId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private void createActivityWithoutPlace(String title, String date) throws Exception {
        mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "activityDate": "%s"}
                                """.formatted(title, date)))
                .andExpect(status().isOk());
    }

    /** 좌표와 도시 식별자를 가진 장소. 도시 일치 판정은 식별자만 본다. */
    private long poiInCity(String name, String cityPlaceRef) {
        Long memberId = memberRepository.findAll().get(0).getId();
        TravelPlace place = placeRepository.save(
                TravelPlace.fromGoogle(memberId, "g-" + UUID.randomUUID(), name));
        place.updateBasics(null, new BigDecimal("34.6650"), new BigDecimal("135.5010"),
                null, null);
        place.updateCityInfo(name, cityPlaceRef, "JP");
        return placeRepository.saveAndFlush(place).getId();
    }

    private void withCityRef(long cityPlaceId, String cityPlaceRef) {
        TravelPlace city = placeRepository.findById(cityPlaceId).orElseThrow();
        city.updateCityInfo(city.getName(), cityPlaceRef, "JP");
        placeRepository.saveAndFlush(city);
    }

    private org.springframework.test.web.servlet.ResultActions board(String date)
            throws Exception {
        return mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                        .param("date", date)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }
}
