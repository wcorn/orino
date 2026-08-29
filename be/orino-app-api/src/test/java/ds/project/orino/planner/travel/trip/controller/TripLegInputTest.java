package ds.project.orino.planner.travel.trip.controller;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.travel.entity.TripDay;
import ds.project.orino.domain.planner.travel.entity.TripStay;
import ds.project.orino.domain.planner.travel.repository.TripActivityRepository;
import ds.project.orino.domain.planner.travel.repository.TripDayRepository;
import ds.project.orino.domain.planner.travel.repository.TripStayRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 구간 입력으로 여행을 만들고 고치는 흐름(v2.1 §3.1·§3.2).
 *
 * <p>확인하는 것은 하나다 — <b>입력은 구간이지만 저장되는 진실은 날짜</b>다. 그래서 단언도
 * 응답이 아니라 {@code trip_day} 행을 본다.
 *
 * <p>합계와 기간이 어긋나도 저장을 막지 않는 것이 규칙이라, 어긋난 채로 저장한 결과가 무엇인지가
 * 곧 사양이다. 400을 기대하는 테스트가 여기 없는 이유다.
 */
class TripLegInputTest extends ApiTestSupport {

    /** 시각을 못박는다. 설정을 나누지 않으므로 컨텍스트가 갈리지 않는다. */
    @Override
    protected Instant fixedNow() {
        return TestClocks.FIXED;
    }

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TripDayRepository dayRepository;
    @Autowired
    private TripStayRepository stayRepository;
    @Autowired
    private TripActivityRepository activityRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private long osaka;
    private long kyoto;
    private long nagoya;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        osaka = city("오사카");
        kyoto = city("교토");
        nagoya = city("나고야");
    }

    @Nested
    @DisplayName("구간 전개")
    class Expansion {

        @Test
        @DisplayName("[오사카 3][교토 1][나고야 1] + 10.24~10.28 → 날짜 5개가 순서대로 붙는다")
        void expandsLegsToDays() throws Exception {
            long tripId = createTrip("2026-10-24", "2026-10-28",
                    leg(osaka, 3), leg(kyoto, 1), leg(nagoya, 1));

            assertThat(citiesOf(tripId)).containsExactly(
                    Map.entry(LocalDate.of(2026, 10, 24), osaka),
                    Map.entry(LocalDate.of(2026, 10, 25), osaka),
                    Map.entry(LocalDate.of(2026, 10, 26), osaka),
                    Map.entry(LocalDate.of(2026, 10, 27), kyoto),
                    Map.entry(LocalDate.of(2026, 10, 28), nagoya));
        }

        @Test
        @DisplayName("합계 5일 / 기간 10일 → 남은 날짜가 마지막 도시를 상속하고 저장된다")
        void shortageIsSavedWithInheritedCity() throws Exception {
            long tripId = createTrip("2026-10-24", "2026-11-02",
                    leg(osaka, 3), leg(kyoto, 1), leg(nagoya, 1));

            assertThat(dayRepository.countByTripId(tripId)).isEqualTo(10);
            assertThat(citiesOf(tripId)).allSatisfy((date, cityId) -> {
                if (date.isAfter(LocalDate.of(2026, 10, 27))) {
                    assertThat(cityId).isEqualTo(nagoya);
                }
            });
        }

        @Test
        @DisplayName("합계 12일 / 기간 4일 → 뒤 구간이 잘리고 저장된다")
        void excessIsSavedTruncated() throws Exception {
            long tripId = createTrip("2026-10-24", "2026-10-27",
                    leg(osaka, 3), leg(kyoto, 4), leg(nagoya, 5));

            assertThat(dayRepository.countByTripId(tripId)).isEqualTo(4);
            assertThat(citiesOf(tripId).values())
                    .containsExactly(osaka, osaka, osaka, kyoto)
                    .doesNotContain(nagoya);
        }

        @Test
        @DisplayName("[도쿄 3][닛코 1][도쿄 2]처럼 같은 도시가 다시 나와도 한 장소를 가리킨다")
        void sameCityReusesPlace() throws Exception {
            long tripId = createTrip("2026-10-24", "2026-10-29",
                    leg(osaka, 3), leg(kyoto, 1), leg(osaka, 2));

            assertThat(citiesOf(tripId).values())
                    .containsExactly(osaka, osaka, osaka, kyoto, osaka, osaka);
        }

        @Test
        @DisplayName("구간 순서를 바꾸면 날짜 범위가 다시 계산된다")
        void reorderingLegsRecalculatesDates() throws Exception {
            long tripId = createTrip("2026-10-24", "2026-10-27", leg(osaka, 2), leg(kyoto, 2));
            assertThat(citiesOf(tripId).values()).containsExactly(osaka, osaka, kyoto, kyoto);

            updateTrip(tripId, "2026-10-24", "2026-10-27", "", leg(kyoto, 2), leg(osaka, 2));

            assertThat(citiesOf(tripId).values()).containsExactly(kyoto, kyoto, osaka, osaka);
        }
    }

    @Nested
    @DisplayName("기간 변경")
    class PeriodChange {

        @Test
        @DisplayName("기간을 늘리면 새 날짜가 마지막 날 도시를 상속한다")
        void extendingInheritsLastCity() throws Exception {
            long tripId = createTrip("2026-10-24", "2026-10-27", leg(osaka, 2), leg(kyoto, 2));

            // 구간을 보내지 않으면 도시 배치는 그대로 두고 기간만 맞춘다.
            updateTrip(tripId, "2026-10-24", "2026-10-29", "");

            assertThat(dayRepository.countByTripId(tripId)).isEqualTo(6);
            assertThat(citiesOf(tripId).values())
                    .containsExactly(osaka, osaka, kyoto, kyoto, kyoto, kyoto);
        }

        @Test
        @DisplayName("기간을 줄이면 잘린 날짜가 사라지고 그 일정은 보관함으로 간다")
        void shrinkingArchivesActivities() throws Exception {
            long tripId = createTrip("2026-10-24", "2026-10-27", leg(osaka, 4));
            createActivity(tripId, "잘리는 일정", "2026-10-27");

            updateTrip(tripId, "2026-10-24", "2026-10-25", "\"confirmArchive\": true");

            assertThat(dayRepository.countByTripId(tripId)).isEqualTo(2);
            assertThat(activityRepository.findUnscheduled(tripId))
                    .extracting(a -> a.getTitle())
                    .containsExactly("잘리는 일정");
        }

        @Test
        @DisplayName("도시 메모는 날짜 기준으로 보존된다 — 도시가 바뀌어도 그 날짜의 메모다")
        void cityMemoSurvivesReexpansion() throws Exception {
            long tripId = createTrip("2026-10-24", "2026-10-27", leg(osaka, 4));
            TripDay day = dayRepository
                    .findByTripIdAndDayDate(tripId, LocalDate.of(2026, 10, 26)).orElseThrow();
            day.updateCityMemo("코인로커에 짐 보관");
            dayRepository.saveAndFlush(day);

            updateTrip(tripId, "2026-10-24", "2026-10-27", "", leg(kyoto, 4));

            TripDay after = dayRepository
                    .findByTripIdAndDayDate(tripId, LocalDate.of(2026, 10, 26)).orElseThrow();
            assertThat(after.getBasePlaceId()).isEqualTo(kyoto);
            assertThat(after.getCityMemo()).isEqualTo("코인로커에 짐 보관");
        }

        @Test
        @DisplayName("기간을 줄이면 걸쳐 있던 숙소의 체크아웃일이 당겨진다")
        void shrinkingPullsStayCheckOut() throws Exception {
            long tripId = createTrip("2026-10-24", "2026-10-29", leg(osaka, 6));
            stayRepository.saveAndFlush(new TripStay(tripId, "오사카 호텔",
                    LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 29)));

            updateTrip(tripId, "2026-10-24", "2026-10-26", "\"confirmArchive\": true");

            assertThat(stayRepository.findAllByTripIdOrderByCheckInDateAscIdAsc(tripId))
                    .singleElement()
                    .satisfies(stay -> assertThat(stay.getCheckOutDate())
                            .isEqualTo(LocalDate.of(2026, 10, 26)));
        }

        @Test
        @DisplayName("당긴 결과 묵는 밤이 없어진 숙소는 지운다")
        void shrinkingRemovesEmptyStay() throws Exception {
            long tripId = createTrip("2026-10-24", "2026-10-29", leg(osaka, 6));
            stayRepository.saveAndFlush(new TripStay(tripId, "교토 료칸",
                    LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 29)));

            updateTrip(tripId, "2026-10-24", "2026-10-26", "\"confirmArchive\": true");

            assertThat(stayRepository.findAllByTripIdOrderByCheckInDateAscIdAsc(tripId)).isEmpty();
        }

        @Test
        @DisplayName("shrink-preview가 일정과 숙소 영향을 함께 알려준다")
        void shrinkPreviewCountsStays() throws Exception {
            long tripId = createTrip("2026-10-24", "2026-10-29", leg(osaka, 6));
            createActivity(tripId, "잘리는 일정", "2026-10-28");
            stayRepository.saveAndFlush(new TripStay(tripId, "오사카 호텔",
                    LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 29)));
            stayRepository.saveAndFlush(new TripStay(tripId, "교토 료칸",
                    LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 30)));

            mockMvc.perform(get("/api/travel/trips/" + tripId + "/shrink-preview")
                            .param("endDate", "2026-10-26")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.movedActivityCount").value(1))
                    .andExpect(jsonPath("$.data.shrunkStayCount").value(1))
                    .andExpect(jsonPath("$.data.removedStayCount").value(1));
        }
    }

    // ---------------- helpers ----------------

    private long city(String name) throws Exception {
        return TravelCityFixture.createCity(mockMvc, authHeader, name, "Asia/Tokyo", "JPY");
    }

    private static String leg(long cityPlaceId, int days) {
        return "{\"cityPlaceId\": %d, \"days\": %d}".formatted(cityPlaceId, days);
    }

    private long createTrip(String start, String end, String... legs) throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "간사이", "startDate": "%s", "endDate": "%s",
                                 "legs": [%s]}
                                """.formatted(start, end, String.join(", ", legs))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** {@code legs}를 주지 않으면 구간을 생략한 요청이 된다(도시 배치를 건드리지 않는다). */
    private void updateTrip(long tripId, String start, String end, String extra, String... legs)
            throws Exception {
        String legsField = legs.length == 0 ? ""
                : ", \"legs\": [%s]".formatted(String.join(", ", legs));
        String extraField = extra.isEmpty() ? "" : ", " + extra;
        mockMvc.perform(put("/api/travel/trips/" + tripId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "간사이", "startDate": "%s", "endDate": "%s"%s%s}
                                """.formatted(start, end, legsField, extraField)))
                .andExpect(status().isOk());
    }

    private void createActivity(long tripId, String title, String date) throws Exception {
        mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"%s\", \"activityDate\": \"%s\"}"
                                .formatted(title, date)))
                .andExpect(status().isOk());
    }

    /** 날짜 → 기준 도시 장소 id. 저장된 진실을 그대로 본다. */
    private Map<LocalDate, Long> citiesOf(long tripId) {
        List<TripDay> days = dayRepository.findAllByTripIdOrderByDayDateAsc(tripId);
        return days.stream().collect(java.util.stream.Collectors.toMap(
                TripDay::getDayDate, TripDay::getBasePlaceId,
                (first, second) -> first, java.util.LinkedHashMap::new));
    }
}
