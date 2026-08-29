package ds.project.orino.planner.travel.trip.controller;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.planner.travel.repository.TripActivityRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.FixedClock;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.TravelCityFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 여행 CRUD 통합 테스트.
 *
 * <p>고정 시각은 {@code 2026-01-15T02:00:00Z}. 상태·D-day는 저장값이 아니라 이 시각과 각 여행의
 * 타임존으로 파생되므로, 날짜를 이 기준 전후로 놓아 세 상태를 모두 만든다.
 */
@FixedClock
class TripControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TripActivityRepository activityRepository;
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

    @Nested
    @DisplayName("POST /trips")
    class Create {

        @Test
        @DisplayName("여행을 만들면 상태·D-day가 파생돼 함께 온다")
        void createsTrip() throws Exception {
            mockMvc.perform(post("/api/travel/trips")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(tripBody("도쿄 3박4일", "도쿄", "2026-10-24", "2026-10-27")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("도쿄 3박4일"))
                    .andExpect(jsonPath("$.data.destinationName").value("도쿄"))
                    .andExpect(jsonPath("$.data.timezone").value("Asia/Tokyo"))
                    .andExpect(jsonPath("$.data.currency").value("JPY"))
                    .andExpect(jsonPath("$.data.status").value("UPCOMING"))
                    .andExpect(jsonPath("$.data.dDay").value(282))
                    .andExpect(jsonPath("$.data.totalDays").value(4))
                    .andExpect(jsonPath("$.data.activityCount").value(0))
                    .andExpect(jsonPath("$.data.defaultNotifyMinutes").value(15));
        }

        @Test
        @DisplayName("제목은 필수다 — 목적지가 여행에 없으니 채울 이름도 없다")
        void rejectsBlankTitle() throws Exception {
            long cityId = TravelCityFixture.createCity(mockMvc, authHeader, "오사카",
                    "Asia/Tokyo", "JPY");
            mockMvc.perform(post("/api/travel/trips")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "  ",
                                     "startDate": "2026-10-24", "endDate": "2026-10-27", %s}
                                    """.formatted(TravelCityFixture.singleLeg(cityId, 4))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("구간이 없으면 400 — 어느 날짜도 기준 도시를 갖지 못한다")
        void rejectsMissingLegs() throws Exception {
            mockMvc.perform(post("/api/travel/trips")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "도쿄", "startDate": "2026-10-24",
                                     "endDate": "2026-10-27"}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("도시가 아닌 장소를 구간에 넣으면 400")
        void rejectsNonCityPlace() throws Exception {
            String place = mockMvc.perform(post("/api/travel/places")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"센소지\"}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            long poiId = ((Number) JsonPath.read(place, "$.data.id")).longValue();

            mockMvc.perform(post("/api/travel/trips")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "도쿄", "startDate": "2026-10-24",
                                     "endDate": "2026-10-27", %s}
                                    """.formatted(TravelCityFixture.singleLeg(poiId, 4))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-016"));
        }

        @Test
        @DisplayName("종료일이 시작일보다 빠르면 400")
        void rejectsInvertedPeriod() throws Exception {
            mockMvc.perform(post("/api/travel/trips")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(tripBody("도쿄", "도쿄", "2026-10-27", "2026-10-24")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-002"));
        }

        @Test
        @DisplayName("제목이 50자를 넘으면 400")
        void rejectsTooLongTitle() throws Exception {
            mockMvc.perform(post("/api/travel/trips")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(tripBody("가".repeat(51), "도쿄", "2026-10-24", "2026-10-27")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("상태 파생")
    class StatusDerivation {

        @Test
        @DisplayName("기간에 따라 예정·진행 중·완료가 갈린다")
        void derivesThreeStatuses() throws Exception {
            createTrip("예정", "도쿄", "2026-10-24", "2026-10-27");
            createTrip("진행중", "오사카", "2026-01-14", "2026-01-16");
            createTrip("완료", "제주", "2025-12-01", "2025-12-03");

            mockMvc.perform(get("/api/travel/trips")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.counts.upcoming").value(1))
                    .andExpect(jsonPath("$.data.counts.ongoing").value(1))
                    .andExpect(jsonPath("$.data.counts.completed").value(1));
        }

        @Test
        @DisplayName("기기 시간대가 아니라 여행 타임존의 오늘로 판정한다")
        void usesTripTimezoneNotDeviceZone() throws Exception {
            // 고정 시각 2026-01-15T02:00Z — 도쿄는 이미 1/15 11:00, 호놀룰루는 아직 1/14 16:00.
            // 같은 1/15 시작 여행이 목적지에 따라 진행 중/예정으로 갈려야 한다.
            long tokyo = createTrip("도쿄", "도쿄", "2026-01-15", "2026-01-18",
                    "Asia/Tokyo", "JPY");
            long honolulu = createTrip("하와이", "호놀룰루", "2026-01-15", "2026-01-18",
                    "Pacific/Honolulu", "USD");

            mockMvc.perform(get("/api/travel/trips/" + tokyo)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.status").value("ONGOING"))
                    .andExpect(jsonPath("$.data.dDay").value(0));
            mockMvc.perform(get("/api/travel/trips/" + honolulu)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.status").value("UPCOMING"))
                    .andExpect(jsonPath("$.data.dDay").value(1));
        }

        @Test
        @DisplayName("status로 거르면 목록만 줄고 탭 건수는 전체 기준을 유지한다")
        void filterDoesNotAffectCounts() throws Exception {
            createTrip("예정", "도쿄", "2026-10-24", "2026-10-27");
            createTrip("완료", "제주", "2025-12-01", "2025-12-03");

            mockMvc.perform(get("/api/travel/trips").param("status", "UPCOMING")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.trips", hasSize(1)))
                    .andExpect(jsonPath("$.data.trips[0].title").value("예정"))
                    .andExpect(jsonPath("$.data.counts.upcoming").value(1))
                    .andExpect(jsonPath("$.data.counts.completed").value(1));
        }

        @Test
        @DisplayName("정렬은 예정·진행 중이 시작일 오름차순으로 먼저, 완료가 종료일 내림차순으로 뒤에")
        void ordersUpcomingFirstThenCompleted() throws Exception {
            createTrip("나중예정", "삿포로", "2026-12-01", "2026-12-04");
            createTrip("오래된완료", "제주", "2025-06-01", "2025-06-03");
            createTrip("곧예정", "도쿄", "2026-10-24", "2026-10-27");
            createTrip("최근완료", "부산", "2025-12-01", "2025-12-03");

            mockMvc.perform(get("/api/travel/trips")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.trips[0].title").value("곧예정"))
                    .andExpect(jsonPath("$.data.trips[1].title").value("나중예정"))
                    .andExpect(jsonPath("$.data.trips[2].title").value("최근완료"))
                    .andExpect(jsonPath("$.data.trips[3].title").value("오래된완료"));
        }
    }

    @Nested
    @DisplayName("도시 요약 (v2.1)")
    class Cities {

        /**
         * 목록이 <b>여행마다 city-legs를 부르지 않는다.</b> 상태 판정을 위해 이미 모든 여행의
         * 날짜별 기준 도시를 한 번에 받아 두었고, 도시 나열은 그 지도를 접기만 한 값이다.
         */
        @Test
        @DisplayName("목록 카드에 구간 순서의 도시가 실려 온다")
        void listCarriesCityPath() throws Exception {
            long osaka = city("오사카");
            long kyoto = city("교토");
            createTripWithLegs("일본", "2026-10-24", "2026-10-27",
                    leg(osaka, 2), leg(kyoto, 2));

            mockMvc.perform(get("/api/travel/trips")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.trips[0].cities.names",
                            contains("오사카", "교토")))
                    .andExpect(jsonPath("$.data.trips[0].cities.count").value(2));
        }

        @Test
        @DisplayName("도쿄 → 닛코 → 도쿄는 셋으로 남고 도시 수는 둘이다")
        void keepsRevisitButCountsDistinct() throws Exception {
            long tokyo = city("도쿄");
            long nikko = city("닛코");
            createTripWithLegs("일본", "2026-10-24", "2026-10-27",
                    leg(tokyo, 1), leg(nikko, 1), leg(tokyo, 2));

            mockMvc.perform(get("/api/travel/trips")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.trips[0].cities.names",
                            contains("도쿄", "닛코", "도쿄")))
                    .andExpect(jsonPath("$.data.trips[0].cities.count").value(2));
        }

        /** 고정 시각은 2026-01-15T02:00Z — 도쿄는 1/15 11:00이라 둘째 날을 지나는 중이다. */
        @Test
        @DisplayName("진행 중이면 오늘의 도시와 그날 타임존·통화가 함께 온다")
        void ongoingCarriesTodayCity() throws Exception {
            long osaka = city("오사카");
            long kyoto = city("교토");
            createTripWithLegs("일본", "2026-01-14", "2026-01-17",
                    leg(osaka, 1), leg(kyoto, 3));

            mockMvc.perform(get("/api/travel/summary")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.ongoing.cities.today").value("교토"))
                    // 어제(오사카)와 다르니 오늘이 옮기는 날이다.
                    .andExpect(jsonPath("$.data.ongoing.cities.movedFrom").value("오사카"))
                    .andExpect(jsonPath("$.data.ongoing.cities.todayDayIndex").value(2))
                    .andExpect(jsonPath("$.data.ongoing.cities.todayTimezone")
                            .value("Asia/Tokyo"))
                    .andExpect(jsonPath("$.data.ongoing.cities.todayCurrency").value("JPY"));
        }

        @Test
        @DisplayName("같은 도시에 머무는 날이면 movedFrom이 없다")
        void staysInSameCity() throws Exception {
            long osaka = city("오사카");
            createTripWithLegs("일본", "2026-01-14", "2026-01-17", leg(osaka, 4));

            mockMvc.perform(get("/api/travel/summary")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.ongoing.cities.today").value("오사카"))
                    .andExpect(jsonPath("$.data.ongoing.cities.movedFrom").doesNotExist());
        }

        @Test
        @DisplayName("예정 여행에는 오늘의 도시가 없다 — 첫날 도시가 오늘인 것처럼 보이면 안 된다")
        void upcomingHasNoToday() throws Exception {
            long tokyo = city("도쿄");
            createTripWithLegs("도쿄", "2026-10-24", "2026-10-27", leg(tokyo, 4));

            mockMvc.perform(get("/api/travel/summary")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.next.cities.names", contains("도쿄")))
                    .andExpect(jsonPath("$.data.next.cities.today").doesNotExist());
        }
    }

    @Nested
    @DisplayName("PUT /trips/{id} — 기간 단축")
    class Shrink {

        @Test
        @DisplayName("기간을 줄여도 잘리는 일정이 없으면 그대로 수정된다")
        void updatesWhenNothingIsCut() throws Exception {
            long tripId = createTrip("도쿄", "도쿄", "2026-10-24", "2026-10-27");
            addActivity(tripId, "센소지", LocalDate.of(2026, 10, 24));

            mockMvc.perform(put("/api/travel/trips/" + tripId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(tripBody("도쿄 단축", "도쿄", "2026-10-24", "2026-10-25")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.endDate").value("2026-10-25"))
                    .andExpect(jsonPath("$.data.totalDays").value(2));
        }

        @Test
        @DisplayName("확인 없이 기간을 줄이면 409로 거부하고 이동 예정 건수를 준다")
        void rejectsShrinkWithoutConfirmation() throws Exception {
            long tripId = createTrip("도쿄", "도쿄", "2026-10-24", "2026-10-27");
            addActivity(tripId, "센소지", LocalDate.of(2026, 10, 26));
            addActivity(tripId, "우에노", LocalDate.of(2026, 10, 27));
            addActivity(tripId, "남는 일정", LocalDate.of(2026, 10, 24));

            mockMvc.perform(put("/api/travel/trips/" + tripId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(tripBody("도쿄", "도쿄", "2026-10-24", "2026-10-25")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-005"))
                    .andExpect(jsonPath("$.data.movedActivityCount").value(2));

            // 거부됐으니 기간도 일정도 그대로여야 한다.
            mockMvc.perform(get("/api/travel/trips/" + tripId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.endDate").value("2026-10-27"));
        }

        @Test
        @DisplayName("confirmArchive=true면 잘린 일정을 지우지 않고 보관함으로 옮긴다")
        void archivesCutActivitiesOnConfirm() throws Exception {
            long tripId = createTrip("도쿄", "도쿄", "2026-10-24", "2026-10-27");
            addActivity(tripId, "남는 일정", LocalDate.of(2026, 10, 24));
            addActivity(tripId, "잘리는 A", LocalDate.of(2026, 10, 26));
            addActivity(tripId, "잘리는 B", LocalDate.of(2026, 10, 27));

            mockMvc.perform(put("/api/travel/trips/" + tripId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "도쿄",
                                     "startDate": "2026-10-24", "endDate": "2026-10-25",
                                     "confirmArchive": true}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.endDate").value("2026-10-25"))
                    // 삭제가 아니라 이동이므로 총 개수는 그대로다.
                    .andExpect(jsonPath("$.data.activityCount").value(3));

            assertArchived(tripId, "잘리는 A", "잘리는 B");
        }

        @Test
        @DisplayName("보관함으로 옮긴 일정은 기존 보관함 뒤에 0..n-1로 이어 붙는다")
        void archivedActivitiesGetSequentialOrder() throws Exception {
            long tripId = createTrip("도쿄", "도쿄", "2026-10-24", "2026-10-27");
            addActivity(tripId, "원래 보관함", null);
            addActivity(tripId, "잘리는 A", LocalDate.of(2026, 10, 26));
            addActivity(tripId, "잘리는 B", LocalDate.of(2026, 10, 27));

            shrinkWithConfirm(tripId, "2026-10-24", "2026-10-25");

            assertThatArchiveOrderIs(tripId, "원래 보관함", "잘리는 A", "잘리는 B");
        }

        @Test
        @DisplayName("시작일을 늦춰 앞이 잘려도 보관함으로 간다")
        void archivesWhenStartDateMovesLater() throws Exception {
            long tripId = createTrip("도쿄", "도쿄", "2026-10-24", "2026-10-27");
            addActivity(tripId, "첫날 일정", LocalDate.of(2026, 10, 24));
            addActivity(tripId, "남는 일정", LocalDate.of(2026, 10, 27));

            shrinkWithConfirm(tripId, "2026-10-26", "2026-10-27");

            assertArchived(tripId, "첫날 일정");
        }

        @Test
        @DisplayName("보관함 일정은 기간을 줄여도 건드리지 않는다")
        void archivedActivitiesAreNotCounted() throws Exception {
            long tripId = createTrip("도쿄", "도쿄", "2026-10-24", "2026-10-27");
            addActivity(tripId, "보관함 후보", null);

            // 잘릴 게 없으므로 확인 없이도 통과해야 한다.
            mockMvc.perform(put("/api/travel/trips/" + tripId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(tripBody("도쿄", "도쿄", "2026-10-24", "2026-10-25")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("기간을 늘리는 수정은 확인 없이 통과한다")
        void extendingPeriodNeedsNoConfirmation() throws Exception {
            long tripId = createTrip("도쿄", "도쿄", "2026-10-24", "2026-10-27");
            addActivity(tripId, "센소지", LocalDate.of(2026, 10, 26));

            mockMvc.perform(put("/api/travel/trips/" + tripId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(tripBody("도쿄", "도쿄", "2026-10-24", "2026-10-30")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.endDate").value("2026-10-30"));
        }

        @Test
        @DisplayName("기준 도시를 바꿔도 일정의 벽시계 시각은 그대로다")
        void baseCityChangeKeepsWallClockTimes() throws Exception {
            long tripId = createTrip("도쿄", "도쿄", "2026-10-24", "2026-10-27");
            long activityId = addActivity(tripId, "09시 출발", LocalDate.of(2026, 10, 24),
                    LocalTime.of(9, 0));

            mockMvc.perform(put("/api/travel/trips/" + tripId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(tripBody("하와이", "호놀룰루", "2026-10-24", "2026-10-27",
                                    "Pacific/Honolulu", "USD")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.timezone").value("Pacific/Honolulu"));

            TripActivity activity = activityRepository.findById(activityId).orElseThrow();
            assertThat(activity.getStartTime())
                    .isEqualTo(LocalTime.of(9, 0));
            assertThat(activity.getActivityDate())
                    .isEqualTo(LocalDate.of(2026, 10, 24));
        }
    }

    @Nested
    @DisplayName("GET /trips/{id}/shrink-preview")
    class ShrinkPreview {

        @Test
        @DisplayName("바꾸려는 기간으로 이동할 일정 수를 미리 준다")
        void previewsMovedCount() throws Exception {
            long tripId = createTrip("도쿄", "도쿄", "2026-10-24", "2026-10-27");
            addActivity(tripId, "A", LocalDate.of(2026, 10, 26));
            addActivity(tripId, "B", LocalDate.of(2026, 10, 27));
            addActivity(tripId, "남음", LocalDate.of(2026, 10, 24));

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/shrink-preview")
                            .param("startDate", "2026-10-24")
                            .param("endDate", "2026-10-25")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.movedActivityCount").value(2));
        }

        @Test
        @DisplayName("생략한 날짜는 현재 기간을 그대로 쓴다")
        void fillsOmittedDatesFromTrip() throws Exception {
            long tripId = createTrip("도쿄", "도쿄", "2026-10-24", "2026-10-27");
            addActivity(tripId, "마지막날", LocalDate.of(2026, 10, 27));

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/shrink-preview")
                            .param("endDate", "2026-10-26")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.movedActivityCount").value(1));
        }

        @Test
        @DisplayName("미리보기는 일정을 옮기지 않는다")
        void previewDoesNotMutate() throws Exception {
            long tripId = createTrip("도쿄", "도쿄", "2026-10-24", "2026-10-27");
            addActivity(tripId, "마지막날", LocalDate.of(2026, 10, 27));

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/shrink-preview")
                            .param("endDate", "2026-10-25")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            assertThat(
                            activityRepository.findUnscheduled(tripId))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /summary")
    class Summary {

        @Test
        @DisplayName("여행이 없으면 세 필드가 전부 null이다")
        void emptySummary() throws Exception {
            mockMvc.perform(get("/api/travel/summary")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.ongoing").doesNotExist())
                    .andExpect(jsonPath("$.data.next").doesNotExist())
                    .andExpect(jsonPath("$.data.recentCompleted").doesNotExist());
        }

        @Test
        @DisplayName("진행 중·다음 예정·최근 완료를 각각 하나씩 준다")
        void picksOnePerBucket() throws Exception {
            long ongoing = createTrip("진행중", "오사카", "2026-01-14", "2026-01-16");
            long soon = createTrip("곧", "도쿄", "2026-10-24", "2026-10-27");
            createTrip("나중", "삿포로", "2026-12-01", "2026-12-04");
            long recent = createTrip("최근완료", "부산", "2025-12-01", "2025-12-03");
            createTrip("오래된완료", "제주", "2025-06-01", "2025-06-03");
            addActivity(soon, "센소지", LocalDate.of(2026, 10, 24));

            mockMvc.perform(get("/api/travel/summary")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.ongoing.id").value((int) ongoing))
                    .andExpect(jsonPath("$.data.ongoing.boardPath")
                            .value("/travel/trips/%d/board".formatted(ongoing)))
                    .andExpect(jsonPath("$.data.next.id").value((int) soon))
                    .andExpect(jsonPath("$.data.next.dDay").value(282))
                    .andExpect(jsonPath("$.data.next.activityCount").value(1))
                    .andExpect(jsonPath("$.data.recentCompleted.id").value((int) recent));
        }
    }

    @Nested
    @DisplayName("소유권")
    class Ownership {

        @Test
        @DisplayName("남의 여행은 조회·수정·삭제 모두 404다(403이 아니다)")
        void otherMembersTripIsNotFound() throws Exception {
            long tripId = createTrip("도쿄", "도쿄", "2026-10-24", "2026-10-27");

            mockMvc.perform(get("/api/travel/trips/" + tripId)
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-001"));
            mockMvc.perform(put("/api/travel/trips/" + tripId)
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(tripBody("탈취", "도쿄", "2026-10-24", "2026-10-27")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete("/api/travel/trips/" + tripId)
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/travel/trips/" + tripId + "/shrink-preview")
                            .param("endDate", "2026-10-25")
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("목록·요약에 남의 여행이 섞이지 않는다")
        void listsAreScopedByMember() throws Exception {
            createTrip("내 여행", "도쿄", "2026-10-24", "2026-10-27");

            mockMvc.perform(get("/api/travel/trips")
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.trips", hasSize(0)))
                    .andExpect(jsonPath("$.data.counts.upcoming").value(0));
            mockMvc.perform(get("/api/travel/summary")
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader))
                    .andExpect(jsonPath("$.data.next").doesNotExist());
        }

        @Test
        @DisplayName("인증 없이는 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/travel/trips"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("DELETE /trips/{id} - 여행을 지우면 일정도 함께 사라진다")
    void deleteCascadesToActivities() throws Exception {
        long tripId = createTrip("도쿄", "도쿄", "2026-10-24", "2026-10-27");
        addActivity(tripId, "센소지", LocalDate.of(2026, 10, 24));

        mockMvc.perform(delete("/api/travel/trips/" + tripId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/travel/trips/" + tripId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound());
        assertThat(activityRepository.countByTripId(tripId))
                .isZero();
    }

    // ---------------- helpers ----------------

    /** 구간 하나짜리 요청 본문. 도시는 미리 만들어 둔 것을 쓴다. */
    private String tripBody(String title, String destination, String start, String end)
            throws Exception {
        return tripBody(title, destination, start, end, "Asia/Tokyo", "JPY");
    }

    private String tripBody(String title, String destination, String start, String end,
                            String timezone, String currency) throws Exception {
        long cityId = TravelCityFixture.createCity(mockMvc, authHeader, destination,
                timezone, currency);
        return """
                {"title": "%s", "startDate": "%s", "endDate": "%s", %s}
                """.formatted(title, start, end, TravelCityFixture.singleLeg(cityId, 1));
    }

    private long city(String name) throws Exception {
        return TravelCityFixture.createCity(mockMvc, authHeader, name, "Asia/Tokyo", "JPY");
    }

    private static String leg(long cityPlaceId, int days) {
        return "{\"cityPlaceId\": %d, \"days\": %d}".formatted(cityPlaceId, days);
    }

    private long createTripWithLegs(String title, String start, String end, String... legs)
            throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "startDate": "%s", "endDate": "%s",
                                 "legs": [%s]}
                                """.formatted(title, start, end, String.join(", ", legs))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private long createTrip(String title, String destination, String start, String end)
            throws Exception {
        return createTrip(title, destination, start, end, "Asia/Tokyo", "JPY");
    }

    private long createTrip(String title, String destination, String start, String end,
                            String timezone, String currency) throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tripBody(title, destination, start, end, timezone, currency)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 일정 API는 #1032에서 나오므로 여기서는 리포지토리로 직접 넣는다. */
    private long addActivity(long tripId, String title, LocalDate date) {
        return addActivity(tripId, title, date, null);
    }

    private long addActivity(long tripId, String title, LocalDate date, LocalTime startTime) {
        int order = activityRepository.nextSortOrder(tripId, date);
        return activityRepository.save(
                new TripActivity(tripId, title, date, order, startTime)).getId();
    }

    private void shrinkWithConfirm(long tripId, String start, String end) throws Exception {
        mockMvc.perform(put("/api/travel/trips/" + tripId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "도쿄",
                                 "startDate": "%s", "endDate": "%s",
                                 "confirmArchive": true}
                                """.formatted(start, end)))
                .andExpect(status().isOk());
    }

    private void assertArchived(long tripId, String... titles) {
        assertThat(activityRepository.findUnscheduled(tripId))
                .extracting(TripActivity::getTitle)
                .containsExactlyInAnyOrder(titles);
    }

    private void assertThatArchiveOrderIs(long tripId, String... titles) {
        assertThat(activityRepository.findUnscheduled(tripId))
                .extracting(TripActivity::getTitle)
                .containsExactly(titles);
        assertThat(activityRepository.findUnscheduled(tripId))
                .extracting(TripActivity::getSortOrder)
                .containsExactly(0, 1, 2);
    }
}
