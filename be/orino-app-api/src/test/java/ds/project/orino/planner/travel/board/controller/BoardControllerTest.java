package ds.project.orino.planner.travel.board.controller;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.FixedClockConfig;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.TravelCityFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보드 단일 조회 통합 테스트.
 *
 * <p>고정 시각 {@code 2026-01-15T02:00:00Z}. 날짜를 생략했을 때 서버가 무엇을 고르는지가
 * 이 화면의 핵심이라 진행 중/예정 여행을 나눠 본다.
 */
@Import(FixedClockConfig.class)
class BoardControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private String otherAuthHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        memberRepository.save(MemberFixture.create("other", "password"));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        otherAuthHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");
    }

    @Test
    @DisplayName("날짜 탭은 기간에서 만들어지고 일정 없는 날도 나온다")
    void buildsDayTabsFromPeriod() throws Exception {
        long tripId = createTrip("도쿄", "2026-10-24", "2026-10-27");
        createActivity(tripId, "센소지", "2026-10-24");
        createActivity(tripId, "우에노", "2026-10-24");

        mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days", hasSize(4)))
                .andExpect(jsonPath("$.data.days[0].dayIndex").value(1))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-10-24"))
                .andExpect(jsonPath("$.data.days[0].weekday").value("토"))
                .andExpect(jsonPath("$.data.days[0].activityCount").value(2))
                .andExpect(jsonPath("$.data.days[1].activityCount").value(0))
                .andExpect(jsonPath("$.data.days[3].dayIndex").value(4))
                // 날씨·이동시간은 각각 4·2단계라 지금은 비어 있다.
                .andExpect(jsonPath("$.data.days[0].weather").doesNotExist())
                .andExpect(jsonPath("$.data.travelTimes", hasSize(0)));
    }

    @Test
    @DisplayName("여행 정보와 기록 모드 플래그가 함께 온다")
    void includesTripHeader() throws Exception {
        long tripId = createTrip("도쿄", "2026-10-24", "2026-10-27");

        mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.trip.title").value("도쿄"))
                .andExpect(jsonPath("$.data.trip.timezone").value("Asia/Tokyo"))
                .andExpect(jsonPath("$.data.trip.currency").value("JPY"))
                .andExpect(jsonPath("$.data.trip.status").value("UPCOMING"))
                .andExpect(jsonPath("$.data.trip.recordMode").value(false));
    }

    @Test
    @DisplayName("완료된 여행은 기록 모드로 내려온다")
    void completedTripIsRecordMode() throws Exception {
        long tripId = createTrip("작년 오사카", "2025-05-01", "2025-05-03");

        mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.trip.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.trip.recordMode").value(true));
    }

    @Test
    @DisplayName("날짜를 생략하면 예정 여행은 1일차를 연다")
    void defaultsToFirstDayWhenUpcoming() throws Exception {
        long tripId = createTrip("도쿄", "2026-10-24", "2026-10-27");
        createActivity(tripId, "1일차 일정", "2026-10-24");
        createActivity(tripId, "2일차 일정", "2026-10-25");

        mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.selectedDate").value("2026-10-24"))
                .andExpect(jsonPath("$.data.activities", hasSize(1)))
                .andExpect(jsonPath("$.data.activities[0].title").value("1일차 일정"));
    }

    @Test
    @DisplayName("날짜를 생략하면 진행 중 여행은 여행 타임존의 오늘을 연다")
    void defaultsToTodayAtDestinationWhenOngoing() throws Exception {
        // 고정 시각 2026-01-15T02:00Z → 도쿄는 1/15, 호놀룰루는 아직 1/14다.
        long tokyo = createTrip("도쿄", "2026-01-13", "2026-01-17", "Asia/Tokyo", "JPY");
        createActivity(tokyo, "오늘 일정", "2026-01-15");
        long honolulu = createTrip("하와이", "2026-01-13", "2026-01-17",
                "Pacific/Honolulu", "USD");

        mockMvc.perform(get("/api/travel/trips/" + tokyo + "/board")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.trip.status").value("ONGOING"))
                .andExpect(jsonPath("$.data.selectedDate").value("2026-01-15"))
                .andExpect(jsonPath("$.data.activities[0].title").value("오늘 일정"));

        // 같은 순간, 같은 기간인데 현지 날짜가 하루 전이라 다른 탭이 열린다.
        mockMvc.perform(get("/api/travel/trips/" + honolulu + "/board")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.selectedDate").value("2026-01-14"));
    }

    @Test
    @DisplayName("date를 주면 그 날짜 일정만 sort_order 순으로 온다")
    void returnsRequestedDateOnly() throws Exception {
        long tripId = createTrip("도쿄", "2026-10-24", "2026-10-27");
        createActivity(tripId, "2일차 A", "2026-10-25");
        createActivity(tripId, "2일차 B", "2026-10-25");
        createActivity(tripId, "1일차", "2026-10-24");

        mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                        .param("date", "2026-10-25")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.selectedDate").value("2026-10-25"))
                .andExpect(jsonPath("$.data.activities", hasSize(2)))
                .andExpect(jsonPath("$.data.activities[0].title").value("2일차 A"))
                .andExpect(jsonPath("$.data.activities[1].title").value("2일차 B"));
    }

    @Test
    @DisplayName("archive=true면 보관함을 보여주고 selectedDate가 비어 있다")
    void returnsArchive() throws Exception {
        long tripId = createTrip("도쿄", "2026-10-24", "2026-10-27");
        createActivity(tripId, "계획됨", "2026-10-24");
        createActivity(tripId, "후보 A", null);
        createActivity(tripId, "후보 B", null);

        mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                        .param("archive", "true")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectedDate").doesNotExist())
                .andExpect(jsonPath("$.data.archiveCount").value(2))
                .andExpect(jsonPath("$.data.activities", hasSize(2)))
                .andExpect(jsonPath("$.data.activities[0].title").value("후보 A"));
    }

    @Test
    @DisplayName("보관함 건수는 어느 탭을 보든 항상 함께 온다")
    void archiveCountAlwaysPresent() throws Exception {
        long tripId = createTrip("도쿄", "2026-10-24", "2026-10-27");
        createActivity(tripId, "후보", null);

        mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                        .param("date", "2026-10-24")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.archiveCount").value(1))
                // 보관함 일정은 날짜 탭 건수에 섞이면 안 된다.
                .andExpect(jsonPath("$.data.days[0].activityCount").value(0));
    }

    @Test
    @DisplayName("기간 밖 날짜를 요청하면 400")
    void rejectsDateOutsideTrip() throws Exception {
        long tripId = createTrip("도쿄", "2026-10-24", "2026-10-27");

        mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                        .param("date", "2026-10-28")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRAVEL-ERR-007"));
    }

    @Test
    @DisplayName("남의 여행 보드는 404")
    void otherMembersBoardIsNotFound() throws Exception {
        long tripId = createTrip("도쿄", "2026-10-24", "2026-10-27");

        mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                        .header(HttpHeaders.AUTHORIZATION, otherAuthHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRAVEL-ERR-001"));
    }

    // ---------------- helpers ----------------

    private long createTrip(String title, String start, String end) throws Exception {
        return createTrip(title, start, end, "Asia/Tokyo", "JPY");
    }

    private long createTrip(String title, String start, String end,
                            String timezone, String currency) throws Exception {
        // 타임존·통화는 여행이 아니라 기준 도시가 갖는다(v2.1).
        long cityId = TravelCityFixture.createCity(mockMvc, authHeader, title,
                timezone, currency);
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "startDate": "%s", "endDate": "%s", %s}
                                """.formatted(title, start, end,
                                        TravelCityFixture.singleLeg(cityId, 1))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private void createActivity(long tripId, String title, String date) throws Exception {
        String dateField = date == null ? "" : ", \"activityDate\": \"%s\"".formatted(date);
        mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"%s\"%s}".formatted(title, dateField)))
                .andExpect(status().isOk());
    }
}
