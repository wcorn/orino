package ds.project.orino.planner.travel.activity.controller;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 일정 CRUD·순서 변경 통합 테스트.
 *
 * <p>고정 시각 {@code 2026-01-15T02:00Z}. 여행 기간은 2026-10-24~10-27로 두어 예정 상태에서
 * 편집하는 상황을 본다.
 */
@Import(FixedClockConfig.class)
class ActivityControllerTest extends ApiTestSupport {

    private static final String DAY1 = "2026-10-24";
    private static final String DAY2 = "2026-10-25";

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TripActivityRepository activityRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private String otherAuthHeader;
    private long tripId;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        memberRepository.save(MemberFixture.create("other", "password"));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        otherAuthHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");
        tripId = createTrip(authHeader);
    }

    @Nested
    @DisplayName("POST /trips/{id}/activities")
    class Create {

        @Test
        @DisplayName("일정을 만들면 서버가 그 날짜 맨 뒤 순서를 배정한다")
        void appendsToEndOfDay() throws Exception {
            createActivity("첫 일정", DAY1);

            mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "둘째 일정", "activityDate": "%s", "startTime": "10:30"}
                                    """.formatted(DAY1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("둘째 일정"))
                    .andExpect(jsonPath("$.data.sortOrder").value(1))
                    .andExpect(jsonPath("$.data.startTime").value("10:30"))
                    .andExpect(jsonPath("$.data.hasLog").value(false))
                    .andExpect(jsonPath("$.data.place").doesNotExist());
        }

        @Test
        @DisplayName("날짜별로 순서를 따로 센다")
        void sortOrderIsPerDate() throws Exception {
            createActivity("1일차", DAY1);

            mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "2일차", "activityDate": "%s"}
                                    """.formatted(DAY2)))
                    .andExpect(jsonPath("$.data.sortOrder").value(0));
        }

        @Test
        @DisplayName("날짜 없이 만들면 보관함으로 들어간다")
        void createsIntoArchive() throws Exception {
            mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "가고 싶은 라멘집"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.activityDate").doesNotExist())
                    .andExpect(jsonPath("$.data.sortOrder").value(0));

            assertThat(activityRepository.findUnscheduled(tripId)).hasSize(1);
        }

        @Test
        @DisplayName("시각 없는 일정도 만들 수 있다")
        void allowsMissingStartTime() throws Exception {
            mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "동네 산책", "activityDate": "%s"}
                                    """.formatted(DAY1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.startTime").doesNotExist());
        }

        @Test
        @DisplayName("여행 기간 밖 날짜는 400")
        void rejectsDateOutsideTrip() throws Exception {
            mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "출발 전날", "activityDate": "2026-10-23"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-007"));
        }

        @Test
        @DisplayName("제목이 없으면 400")
        void rejectsBlankTitle() throws Exception {
            mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "  ", "activityDate": "%s"}
                                    """.formatted(DAY1)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("남의 여행에는 일정을 넣을 수 없다(404)")
        void rejectsOtherMembersTrip() throws Exception {
            mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "침입", "activityDate": "%s"}
                                    """.formatted(DAY1)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-001"));
        }
    }

    @Nested
    @DisplayName("PUT · DELETE /activities/{id}")
    class UpdateAndDelete {

        @Test
        @DisplayName("계획 필드를 한 번에 수정한다")
        void updatesPlanFields() throws Exception {
            long id = createActivity("센소지", DAY1);

            mockMvc.perform(put("/api/travel/activities/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "센소지 재방문", "activityDate": "%s",
                                     "startTime": "09:15", "memo": "티켓 예매함",
                                     "url": "https://example.com", "notifyEnabled": true,
                                     "notifyMinutes": 30, "departureNotifyEnabled": true}
                                    """.formatted(DAY1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("센소지 재방문"))
                    .andExpect(jsonPath("$.data.startTime").value("09:15"))
                    .andExpect(jsonPath("$.data.memo").value("티켓 예매함"))
                    .andExpect(jsonPath("$.data.notifyEnabled").value(true))
                    .andExpect(jsonPath("$.data.notifyMinutes").value(30))
                    .andExpect(jsonPath("$.data.departureNotifyEnabled").value(true));
        }

        @Test
        @DisplayName("시각 없이 알림을 켜도 저장은 된다(알림 생성 여부는 3단계 판정)")
        void allowsNotifyWithoutTime() throws Exception {
            long id = createActivity("쇼핑", DAY1);

            mockMvc.perform(put("/api/travel/activities/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "쇼핑", "activityDate": "%s", "notifyEnabled": true}
                                    """.formatted(DAY1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.notifyEnabled").value(true))
                    .andExpect(jsonPath("$.data.startTime").doesNotExist());

            assertThat(activityRepository.findById(id).orElseThrow().isNotifiable()).isFalse();
        }

        @Test
        @DisplayName("날짜를 바꾸면 옮겨간 날짜의 맨 뒤로 붙고, 떠나온 날짜는 0..n-1로 메워진다")
        void movingDateReindexesBothSides() throws Exception {
            long a = createActivity("A", DAY1);
            long b = createActivity("B", DAY1);
            long c = createActivity("C", DAY1);
            createActivity("기존 2일차", DAY2);

            mockMvc.perform(put("/api/travel/activities/" + b)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "B", "activityDate": "%s"}
                                    """.formatted(DAY2)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sortOrder").value(1));

            assertOrder(DAY1, List.of(a, c));
            assertThat(activityRepository.findById(b).orElseThrow().getActivityDate())
                    .isEqualTo(LocalDate.parse(DAY2));
        }

        @Test
        @DisplayName("보관함으로 내리면 날짜가 비고 보관함 맨 뒤에 붙는다")
        void movesToArchive() throws Exception {
            createActivity("기존 보관함", null);
            long id = createActivity("내릴 일정", DAY1);

            mockMvc.perform(put("/api/travel/activities/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "내릴 일정"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.activityDate").doesNotExist())
                    .andExpect(jsonPath("$.data.sortOrder").value(1));
        }

        @Test
        @DisplayName("기간 밖으로는 옮길 수 없다(400)")
        void rejectsMoveOutsideTrip() throws Exception {
            long id = createActivity("센소지", DAY1);

            mockMvc.perform(put("/api/travel/activities/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "센소지", "activityDate": "2026-11-01"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-007"));
        }

        @Test
        @DisplayName("삭제하면 남은 일정의 순서가 0..n-1로 메워진다")
        void deleteReindexesRemaining() throws Exception {
            long a = createActivity("A", DAY1);
            long b = createActivity("B", DAY1);
            long c = createActivity("C", DAY1);

            mockMvc.perform(delete("/api/travel/activities/" + b)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            assertOrder(DAY1, List.of(a, c));
        }

        @Test
        @DisplayName("남의 일정은 조회·수정·삭제 모두 404다")
        void otherMembersActivityIsNotFound() throws Exception {
            long id = createActivity("센소지", DAY1);

            mockMvc.perform(get("/api/travel/activities/" + id)
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-006"));
            mockMvc.perform(put("/api/travel/activities/" + id)
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "탈취", "activityDate": "%s"}
                                    """.formatted(DAY1)))
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete("/api/travel/activities/" + id)
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /trips/{id}/activities/order")
    class Reorder {

        @Test
        @DisplayName("보낸 배열 순서대로 0..n-1을 다시 매긴다")
        void reassignsSortOrder() throws Exception {
            long a = createActivity("A", DAY1);
            long b = createActivity("B", DAY1);
            long c = createActivity("C", DAY1);

            reorder("""
                    {"moves": [{"date": "%s", "activityIds": [%d, %d, %d]}]}
                    """.formatted(DAY1, c, a, b));

            assertOrder(DAY1, List.of(c, a, b));
        }

        @Test
        @DisplayName("순서 변경과 날짜 이동이 한 요청으로 처리된다")
        void movesAcrossDatesInOneRequest() throws Exception {
            long a = createActivity("A", DAY1);
            long b = createActivity("B", DAY1);
            long c = createActivity("C", DAY2);

            // A를 2일차 맨 앞으로 보내고, 1일차엔 B만 남긴다.
            reorder("""
                    {"moves": [
                        {"date": "%s", "activityIds": [%d]},
                        {"date": "%s", "activityIds": [%d, %d]}
                    ]}
                    """.formatted(DAY1, b, DAY2, a, c));

            assertOrder(DAY1, List.of(b));
            assertOrder(DAY2, List.of(a, c));
        }

        @Test
        @DisplayName("보관함으로 내리고 올리는 것도 같은 요청으로 된다")
        void movesBetweenArchiveAndDate() throws Exception {
            long planned = createActivity("계획됨", DAY1);
            long archived = createActivity("보관됨", null);

            reorder("""
                    {"moves": [
                        {"date": null, "activityIds": [%d]},
                        {"date": "%s", "activityIds": [%d]}
                    ]}
                    """.formatted(planned, DAY1, archived));

            assertThat(activityRepository.findUnscheduled(tripId))
                    .extracting(TripActivity::getId).containsExactly(planned);
            assertOrder(DAY1, List.of(archived));
        }

        @Test
        @DisplayName("일부만 보내도 남은 일정이 뒤에 붙어 순서에 구멍이 남지 않는다")
        void normalizesEvenOnPartialArray() throws Exception {
            long a = createActivity("A", DAY1);
            long b = createActivity("B", DAY1);
            long c = createActivity("C", DAY1);

            // C만 보냈다 — 나머지는 뒤로 밀리되 0..n-1은 유지돼야 한다.
            reorder("""
                    {"moves": [{"date": "%s", "activityIds": [%d]}]}
                    """.formatted(DAY1, c));

            assertOrder(DAY1, List.of(c, a, b));
        }

        @Test
        @DisplayName("기간 밖 날짜로는 옮길 수 없다(400)")
        void rejectsDateOutsideTrip() throws Exception {
            long a = createActivity("A", DAY1);

            mockMvc.perform(put("/api/travel/trips/" + tripId + "/activities/order")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"moves": [{"date": "2026-12-01", "activityIds": [%d]}]}
                                    """.formatted(a)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-007"));
        }

        @Test
        @DisplayName("남의 일정이 섞이면 통째로 막고 아무것도 바꾸지 않는다")
        void rejectsForeignActivityAndChangesNothing() throws Exception {
            long a = createActivity("A", DAY1);
            long b = createActivity("B", DAY1);
            long foreign = createForeignActivity();

            mockMvc.perform(put("/api/travel/trips/" + tripId + "/activities/order")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"moves": [{"date": "%s", "activityIds": [%d, %d, %d]}]}
                                    """.formatted(DAY1, b, a, foreign)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-006"));

            // 실패했으니 원래 순서 그대로.
            assertOrder(DAY1, List.of(a, b));
        }

        @Test
        @DisplayName("남의 여행 순서는 바꿀 수 없다(404)")
        void rejectsOtherMembersTrip() throws Exception {
            long a = createActivity("A", DAY1);

            mockMvc.perform(put("/api/travel/trips/" + tripId + "/activities/order")
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"moves": [{"date": "%s", "activityIds": [%d]}]}
                                    """.formatted(DAY1, a)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-001"));
        }
    }

    // ---------------- helpers ----------------

    private long createTrip(String header) throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, header)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "도쿄",
                                 "startDate": "2026-10-24", "endDate": "2026-10-27",
                                 %s}
                                """.formatted(TravelCityFixture.singleLeg(
                                        cityId(header), 4))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private long createActivity(String title, String date) throws Exception {
        String dateField = date == null ? "" : ", \"activityDate\": \"%s\"".formatted(date);
        String body = mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"%s\"%s}".formatted(title, dateField)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 다른 멤버의 여행에 속한 일정 — 소유권 검사를 실증하기 위한 것. */
    private long createForeignActivity() throws Exception {
        long foreignTrip = createTrip(otherAuthHeader);
        String body = mockMvc.perform(post("/api/travel/trips/" + foreignTrip + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, otherAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "남의 일정", "activityDate": "%s"}
                                """.formatted(DAY1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private void reorder(String body) throws Exception {
        mockMvc.perform(put("/api/travel/trips/" + tripId + "/activities/order")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.travelTimes").isArray());
    }

    private void assertOrder(String date, List<Long> expectedIds) {
        List<TripActivity> ordered = activityRepository
                .findAllByTripIdAndActivityDateOrderBySortOrderAscIdAsc(tripId, LocalDate.parse(date));
        assertThat(ordered).extracting(TripActivity::getId).containsExactlyElementsOf(expectedIds);
        // 순서 값 자체가 0..n-1이어야 다음 드래그가 어긋나지 않는다.
        assertThat(ordered).extracting(TripActivity::getSortOrder)
                .containsExactlyElementsOf(IntStream.range(0, expectedIds.size()).boxed().toList());
    }

    private long cityId(String header) throws Exception {
        return TravelCityFixture.createCity(mockMvc, header, "도쿄", "Asia/Tokyo", "JPY");
    }

}
