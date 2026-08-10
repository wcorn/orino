package ds.project.orino.planner.travel.day;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.push.entity.NotificationStatus;
import ds.project.orino.domain.planner.push.entity.PushNotification;
import ds.project.orino.domain.planner.push.repository.PushNotificationRepository;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.planner.travel.repository.TripActivityRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.FixedClockConfig;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.TravelCityFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 날짜 조회 · 기준 도시 변경 · 구간 파생(§4.5).
 *
 * <p>기준 도시 변경이 <b>연쇄를 부르는지</b>가 이 테스트의 핵심이다 — 화면만 바뀌고 알림이
 * 옛 시각에 남으면 사용자가 알아차릴 방법이 없다. 그래서 알림의 {@code scheduled_at}을
 * 직접 확인한다.
 */
@Import(FixedClockConfig.class)
class TripDayControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TripActivityRepository activityRepository;
    @Autowired
    private PushNotificationRepository notificationRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private String otherAuthHeader;
    private long tokyo;
    private long nikko;
    private long tripId;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        memberRepository.save(MemberFixture.create("other", "password"));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        otherAuthHeader = "Bearer "
                + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");

        tokyo = TravelCityFixture.createCity(mockMvc, authHeader, "도쿄", "Asia/Tokyo", "JPY");
        nikko = TravelCityFixture.createCity(mockMvc, authHeader, "닛코", "Asia/Tokyo", "JPY");
        tripId = createTrip();
    }

    @Nested
    @DisplayName("GET /trips/{tripId}/days")
    class Days {

        @Test
        @DisplayName("기간 전체의 날짜에 기준 도시가 붙어 온다")
        void listsDaysWithBaseCity() throws Exception {
            mockMvc.perform(get("/api/travel/trips/" + tripId + "/days")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(4)))
                    .andExpect(jsonPath("$.data[0].dayIndex").value(1))
                    .andExpect(jsonPath("$.data[0].date").value("2026-10-24"))
                    .andExpect(jsonPath("$.data[0].weekday").value("토"))
                    .andExpect(jsonPath("$.data[0].baseCity.name").value("도쿄"))
                    .andExpect(jsonPath("$.data[0].baseCity.timezone").value("Asia/Tokyo"))
                    .andExpect(jsonPath("$.data[0].baseCity.currency").value("JPY"))
                    .andExpect(jsonPath("$.data[0].legIndex").value(1))
                    // 첫날은 "바뀐 것"이 아니다 — 비교할 앞 날짜가 없다.
                    .andExpect(jsonPath("$.data[0].cityChanged").value(false))
                    .andExpect(jsonPath("$.data[3].dayIndex").value(4));
        }

        @Test
        @DisplayName("남의 여행 날짜는 404")
        void scopedByMember() throws Exception {
            mockMvc.perform(get("/api/travel/trips/" + tripId + "/days")
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-001"));
        }
    }

    @Nested
    @DisplayName("PUT /days/{dayId}")
    class ChangeBaseCity {

        @Test
        @DisplayName("기준 도시를 바꾸면 그 날짜만 바뀌고 구간이 쪼개진다")
        void changesOneDayAndSplitsLegs() throws Exception {
            changeBaseCity(dayIdAt(1), nikko);

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/days")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data[0].baseCity.name").value("도쿄"))
                    .andExpect(jsonPath("$.data[1].baseCity.name").value("닛코"))
                    .andExpect(jsonPath("$.data[2].baseCity.name").value("도쿄"))
                    // 도시가 바뀌는 날짜마다 탭에 구분선이 붙는다.
                    .andExpect(jsonPath("$.data[1].cityChanged").value(true))
                    .andExpect(jsonPath("$.data[2].cityChanged").value(true))
                    .andExpect(jsonPath("$.data[3].cityChanged").value(false))
                    .andExpect(jsonPath("$.data[1].legIndex").value(2))
                    .andExpect(jsonPath("$.data[2].legIndex").value(3));
        }

        @Test
        @DisplayName("응답은 기간 전체의 날짜다 — 하루를 바꾸면 앞뒤 표시까지 달라진다")
        void returnsWholePeriod() throws Exception {
            mockMvc.perform(put("/api/travel/days/" + dayIdAt(1))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"baseCityPlaceId\": %d}".formatted(nikko)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(4)))
                    .andExpect(jsonPath("$.data[1].baseCity.name").value("닛코"));
        }

        @Test
        @DisplayName("도시를 바꾸면 그 날짜 일정의 PENDING 알림 발송시각이 실제로 바뀐다")
        void reschedulesNotifications() throws Exception {
            // 방콕(UTC+7)은 도쿄(UTC+9)보다 2시간 늦다 — 09:00은 그대로고 그 순간만 바뀐다.
            long bangkok = TravelCityFixture.createCity(mockMvc, authHeader, "방콕",
                    "Asia/Bangkok", "THB");
            createActivity("2026-10-25", "09:00");
            Instant before = pendingScheduledAt();
            assertThat(before).isEqualTo(Instant.parse("2026-10-24T23:45:00Z"));

            changeBaseCity(dayIdAt(1), bangkok);

            assertThat(pendingScheduledAt()).isEqualTo(Instant.parse("2026-10-25T01:45:00Z"));
        }

        @Test
        @DisplayName("도시를 바꿔도 그 날짜 일정의 장소는 그대로다 — 경고도 띄우지 않는다")
        void keepsActivityPlaces() throws Exception {
            long placeId = createPoi("센소지");
            long activityId = createActivityWithPlace("2026-10-25", placeId);

            changeBaseCity(dayIdAt(1), nikko);

            TripActivity activity = activityRepository.findById(activityId).orElseThrow();
            assertThat(activity.getPlaceId()).isEqualTo(placeId);
        }

        @Test
        @DisplayName("도시 메모만 보내면 기준 도시는 건드리지 않는다")
        void updatesMemoOnly() throws Exception {
            mockMvc.perform(put("/api/travel/days/" + dayIdAt(1))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"cityMemo": "체크아웃 후 코인로커에 짐 보관"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[1].cityMemo")
                            .value("체크아웃 후 코인로커에 짐 보관"))
                    .andExpect(jsonPath("$.data[1].baseCity.name").value("도쿄"));
        }

        @Test
        @DisplayName("도시가 아닌 장소를 기준 도시로 지정하면 400")
        void rejectsNonCityPlace() throws Exception {
            long poiId = createPoi("센소지");

            mockMvc.perform(put("/api/travel/days/" + dayIdAt(1))
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"baseCityPlaceId\": %d}".formatted(poiId)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-016"));
        }

        @Test
        @DisplayName("남의 여행 날짜는 404")
        void scopedByMember() throws Exception {
            mockMvc.perform(put("/api/travel/days/" + dayIdAt(0))
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"cityMemo\": \"남의 메모\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-019"));
        }
    }

    @Nested
    @DisplayName("GET /trips/{tripId}/city-legs")
    class CityLegs {

        @Test
        @DisplayName("전 기간 같은 도시면 구간 1개")
        void singleLeg() throws Exception {
            mockMvc.perform(get("/api/travel/trips/" + tripId + "/city-legs")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].legIndex").value(1))
                    .andExpect(jsonPath("$.data[0].cityName").value("도쿄"))
                    .andExpect(jsonPath("$.data[0].days").value(4))
                    .andExpect(jsonPath("$.data[0].startDate").value("2026-10-24"))
                    .andExpect(jsonPath("$.data[0].endDate").value("2026-10-27"))
                    .andExpect(jsonPath("$.data[0].timezone").value("Asia/Tokyo"));
        }

        @Test
        @DisplayName("[도쿄, 닛코, 도쿄] → 구간 3개. 저장된 것이 아니라 매번 파생한다")
        void splitsIntoThree() throws Exception {
            changeBaseCity(dayIdAt(1), nikko);

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/city-legs")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data", hasSize(3)))
                    .andExpect(jsonPath("$.data[0].cityName").value("도쿄"))
                    .andExpect(jsonPath("$.data[0].days").value(1))
                    .andExpect(jsonPath("$.data[1].cityName").value("닛코"))
                    .andExpect(jsonPath("$.data[2].cityName").value("도쿄"))
                    .andExpect(jsonPath("$.data[2].days").value(2))
                    .andExpect(jsonPath("$.data[2].legIndex").value(3));
        }
    }

    // ---------------- helpers ----------------

    private long createTrip() throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "도쿄", "startDate": "2026-10-24",
                                 "endDate": "2026-10-27", %s}
                                """.formatted(TravelCityFixture.singleLeg(tokyo, 4))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 날짜 목록에서 {@code index}번째 날짜의 id. */
    private long dayIdAt(int index) throws Exception {
        String body = mockMvc.perform(get("/api/travel/trips/" + tripId + "/days")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data[%d].dayId".formatted(index))).longValue();
    }

    /** 도시가 아닌 일반 장소. 기준 도시로는 쓸 수 없고 일정에는 붙는다. */
    private long createPoi(String name) throws Exception {
        String body = mockMvc.perform(post("/api/travel/places")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\"}".formatted(name)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private void changeBaseCity(long dayId, long cityPlaceId) throws Exception {
        mockMvc.perform(put("/api/travel/days/" + dayId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseCityPlaceId\": %d}".formatted(cityPlaceId)))
                .andExpect(status().isOk());
    }

    private void createActivity(String date, String startTime) throws Exception {
        mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "센소지", "activityDate": "%s", "startTime": "%s",
                                 "notifyEnabled": true}
                                """.formatted(date, startTime)))
                .andExpect(status().isOk());
    }

    private long createActivityWithPlace(String date, long placeId) throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "센소지", "activityDate": "%s", "placeId": %d}
                                """.formatted(date, placeId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private Instant pendingScheduledAt() {
        List<PushNotification> pending = notificationRepository.findAll().stream()
                .filter(n -> n.getStatus() == NotificationStatus.PENDING)
                .filter(n -> n.getActivityId() != null)
                .toList();
        assertThat(pending).singleElement();
        return pending.getFirst().getScheduledAt();
    }
}
