package ds.project.orino.planner.travel.activity.controller;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.travel.repository.TripActivityLogRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.TestClocks;
import ds.project.orino.support.TravelCityFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 기록(평점·메모) 통합 테스트(§S-07 기록 영역).
 *
 * <p>고정 시각 {@code 2026-01-15T02:00Z}. 여행 두 개를 만든다 — <b>진행 중</b>(1/10~1/20)과
 * <b>예정</b>(10/24~10/27). 기록이 시작일 기준으로 갈리는 기능이라 두 상태가 다 필요하다.
 */
class ActivityLogTest extends ApiTestSupport {

    /** 시각을 못박는다. 설정을 나누지 않으므로 컨텍스트가 갈리지 않는다. */
    @Override
    protected Instant fixedNow() {
        return TestClocks.FIXED;
    }

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TripActivityLogRepository logRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private long ongoingActivityId;
    private long upcomingActivityId;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);

        long ongoingTrip = createTrip("2026-01-10", "2026-01-20");
        ongoingActivityId = createActivity(ongoingTrip, "센소지", "2026-01-15");
        long upcomingTrip = createTrip("2026-10-24", "2026-10-27");
        upcomingActivityId = createActivity(upcomingTrip, "시부야", "2026-10-24");
    }

    @Nested
    @DisplayName("PUT /activities/{id}/log")
    class SaveLog {

        @Test
        @DisplayName("평점과 메모를 저장한다")
        void savesRatingAndMemo() throws Exception {
            saveLog(ongoingActivityId, """
                    {"rating": 4, "memo": "야경이 좋았다"}
                    """)
                    .andExpect(jsonPath("$.data.rating").value(4))
                    .andExpect(jsonPath("$.data.memo").value("야경이 좋았다"));

            assertThat(logRepository.findByActivityId(ongoingActivityId)).isPresent();
        }

        @Test
        @DisplayName("다시 저장하면 덮어쓴다 — 일정당 기록은 하나다")
        void upsertsInsteadOfAppending() throws Exception {
            saveLog(ongoingActivityId, """
                    {"rating": 2, "memo": "그냥 그랬다"}
                    """);
            saveLog(ongoingActivityId, """
                    {"rating": 5, "memo": "다시 가고 싶다"}
                    """)
                    .andExpect(jsonPath("$.data.rating").value(5));

            assertThat(logRepository.count()).isEqualTo(1);
            assertThat(logRepository.findByActivityId(ongoingActivityId).orElseThrow().getMemo())
                    .isEqualTo("다시 가고 싶다");
        }

        @Test
        @DisplayName("평점만 지울 수 있다 — 잘못 누른 별을 되돌릴 방법이 있어야 한다")
        void clearsRatingButKeepsMemo() throws Exception {
            saveLog(ongoingActivityId, """
                    {"rating": 1, "memo": "메모는 남긴다"}
                    """);

            saveLog(ongoingActivityId, """
                    {"rating": null, "memo": "메모는 남긴다"}
                    """)
                    .andExpect(jsonPath("$.data.rating").doesNotExist())
                    .andExpect(jsonPath("$.data.memo").value("메모는 남긴다"));
        }

        @Test
        @DisplayName("평점·메모를 모두 비우면 기록 자체가 사라진다 — 빈 기록은 기록이 아니다")
        void removesLogWhenEverythingCleared() throws Exception {
            saveLog(ongoingActivityId, """
                    {"rating": 3, "memo": "적어둔다"}
                    """);

            saveLog(ongoingActivityId, """
                    {"rating": null, "memo": "  "}
                    """)
                    .andExpect(jsonPath("$.data").doesNotExist());

            assertThat(logRepository.findByActivityId(ongoingActivityId)).isEmpty();
            mockMvc.perform(get("/api/travel/activities/" + ongoingActivityId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.hasLog").value(false));
        }

        @Test
        @DisplayName("여행 시작 전에는 400 — 일정은 실재하니 404가 아니다")
        void rejectsBeforeTripStart() throws Exception {
            mockMvc.perform(put("/api/travel/activities/" + upcomingActivityId + "/log")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"rating": 5, "memo": "가지도 않았는데"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-012"));

            assertThat(logRepository.findByActivityId(upcomingActivityId)).isEmpty();
        }

        @Test
        @DisplayName("평점 범위를 벗어나면 400")
        void rejectsRatingOutOfRange() throws Exception {
            mockMvc.perform(put("/api/travel/activities/" + ongoingActivityId + "/log")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"rating": 6}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("남의 일정에는 기록할 수 없다 — 404로 존재조차 흘리지 않는다")
        void rejectsForeignActivity() throws Exception {
            memberRepository.save(MemberFixture.create("other", "password"));
            String otherHeader =
                    "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");

            mockMvc.perform(put("/api/travel/activities/" + ongoingActivityId + "/log")
                            .header(HttpHeaders.AUTHORIZATION, otherHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"rating": 5}
                                    """))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("조회에 기록이 붙는다")
    class ReadBack {

        @Test
        @DisplayName("상세에 기록이 그대로 실린다")
        void detailCarriesLog() throws Exception {
            saveLog(ongoingActivityId, """
                    {"rating": 5, "memo": "최고"}
                    """);

            mockMvc.perform(get("/api/travel/activities/" + ongoingActivityId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.hasLog").value(true))
                    .andExpect(jsonPath("$.data.log.rating").value(5))
                    .andExpect(jsonPath("$.data.log.memo").value("최고"));
        }

        @Test
        @DisplayName("보드 목록에도 기록이 실린다 — 보드 응답이 곧 오프라인 캐시다")
        void boardCarriesLog() throws Exception {
            saveLog(ongoingActivityId, """
                    {"rating": 4, "memo": "야경"}
                    """);

            long tripId = tripOf(ongoingActivityId);
            mockMvc.perform(get("/api/travel/trips/" + tripId + "/board")
                            .param("date", "2026-01-15")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.activities[0].hasLog").value(true))
                    .andExpect(jsonPath("$.data.activities[0].log.memo").value("야경"));
        }

        @Test
        @DisplayName("기록이 없으면 log는 null이고 hasLog는 false다")
        void absentLogIsNull() throws Exception {
            mockMvc.perform(get("/api/travel/activities/" + ongoingActivityId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.log").doesNotExist())
                    .andExpect(jsonPath("$.data.hasLog").value(false));
        }
    }

    // ---------------- helpers ----------------

    private org.springframework.test.web.servlet.ResultActions saveLog(long activityId, String body)
            throws Exception {
        return mockMvc.perform(put("/api/travel/activities/" + activityId + "/log")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private long createTrip(String startDate, String endDate) throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "도쿄",
                                 "startDate": "%s", "endDate": "%s",
                                 %s}
                                """.formatted(startDate, endDate,
                                        TravelCityFixture.singleLeg(cityId(), 1))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private long createActivity(long tripId, String title, String date) throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "activityDate": "%s"}
                                """.formatted(title, date)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private long tripOf(long activityId) throws Exception {
        String body = mockMvc.perform(get("/api/travel/activities/" + activityId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.tripId")).longValue();
    }

    private long cityId() throws Exception {
        return TravelCityFixture.createCity(mockMvc, authHeader, "도쿄", "Asia/Tokyo", "JPY");
    }

}
