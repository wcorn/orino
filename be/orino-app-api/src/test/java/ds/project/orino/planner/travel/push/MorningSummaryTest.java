package ds.project.orino.planner.travel.push;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.push.entity.NotificationStatus;
import ds.project.orino.domain.planner.push.entity.NotificationType;
import ds.project.orino.domain.planner.push.entity.PushNotification;
import ds.project.orino.domain.planner.push.entity.PushSubscription;
import ds.project.orino.domain.planner.push.repository.PushNotificationRepository;
import ds.project.orino.domain.planner.push.repository.PushSubscriptionRepository;
import ds.project.orino.planner.travel.push.send.WebPushSender;
import ds.project.orino.planner.travel.push.service.NotificationDispatchService;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.TravelCityFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 아침 요약(§4.3).
 *
 * <p>앞의 둘과 다른 점은 <b>일정이 아니라 날짜에 매달려</b> 있다는 것이다. 그래서 0건 판정을
 * 예약 때가 아니라 <b>보내기 직전</b>에 한다 — 나중에 채운 날은 요약이 와야 하고, 다 지운
 * 날엔 "일정 0개"가 가면 안 된다.
 */
class MorningSummaryTest extends ApiTestSupport {

    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/morning-device";

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PushSubscriptionRepository subscriptionRepository;
    @Autowired
    private PushNotificationRepository notificationRepository;
    @Autowired
    private NotificationDispatchService dispatchService;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private WebPushSender sender;

    private StubWebPushSender stub;
    private String authHeader;
    private Long memberId;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        stub = (StubWebPushSender) sender;
        stub.reset();

        memberRepository.save(MemberFixture.create());
        memberId = memberRepository.findAll().get(0).getId();
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        subscriptionRepository.save(
                new PushSubscription(memberId, ENDPOINT, "p256dh", "auth", "Android"));
    }

    /** 과거 날짜로 만든다 — 08:00이 이미 지나 있어야 발송 대상이 된다. */
    private long createTrip(boolean morningSummary) throws Exception {
        long cityId = tokyoCityId();
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "도쿄",
                                 "startDate": "2020-01-01", "endDate": "2020-01-03",
                                 %s,
                                 "morningSummaryEnabled": %s}
                                """.formatted(TravelCityFixture.singleLeg(cityId, 3),
                                        morningSummary)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.id")).longValue();
    }

    private void addActivity(long tripId, String title, String date, String time)
            throws Exception {
        String timeField = time == null ? "" : ", \"startTime\": \"%s\"".formatted(time);
        mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "activityDate": "%s"%s}
                                """.formatted(title, date, timeField)))
                .andExpect(status().isOk());
    }

    private List<PushNotification> morningNotifications() {
        return notificationRepository.findAllByMemberIdOrderByScheduledAtAsc(memberId).stream()
                .filter(n -> n.getType() == NotificationType.MORNING_SUMMARY)
                .toList();
    }

    @Nested
    @DisplayName("예약")
    class Scheduling {

        @Test
        @DisplayName("여행 기간 모든 날짜에 현지 08:00으로 잡는다")
        void schedulesEveryDayAtEight() throws Exception {
            createTrip(true);

            // 1/1~1/3 사흘.
            assertThat(morningNotifications()).hasSize(3);
            // 도쿄 08:00 = UTC 전날 23:00.
            assertThat(morningNotifications().get(0).getScheduledAt())
                    .isEqualTo(Instant.parse("2019-12-31T23:00:00Z"));
        }

        @Test
        @DisplayName("일정이 하나도 없어도 잡는다 — 0건 판정은 보낼 때 한다")
        void schedulesEvenWithoutActivities() throws Exception {
            createTrip(true);

            assertThat(morningNotifications()).hasSize(3);
            assertThat(morningNotifications())
                    .allSatisfy(n -> assertThat(n.getActivityId()).isNull());
        }

        @Test
        @DisplayName("스위치가 꺼져 있으면 만들지 않는다")
        void skipsWhenDisabled() throws Exception {
            createTrip(false);

            assertThat(morningNotifications()).isEmpty();
        }

        @Test
        @DisplayName("나중에 켜면 그때 잡힌다")
        void schedulesWhenEnabledLater() throws Exception {
            long tripId = createTrip(false);
            assertThat(morningNotifications()).isEmpty();

            mockMvc.perform(put("/api/travel/trips/" + tripId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "도쿄",
                                     "startDate": "2020-01-01", "endDate": "2020-01-03",
                                     "morningSummaryEnabled": true}
                                    """))
                    .andExpect(status().isOk());

            assertThat(morningNotifications()).hasSize(3);
        }

        @Test
        @DisplayName("기간을 줄이면 잘린 날짜의 요약도 사라진다")
        void reschedulesOnPeriodChange() throws Exception {
            long tripId = createTrip(true);
            assertThat(morningNotifications()).hasSize(3);

            mockMvc.perform(put("/api/travel/trips/" + tripId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "도쿄",
                                     "startDate": "2020-01-01", "endDate": "2020-01-02",
                                     "morningSummaryEnabled": true, "confirmArchive": true}
                                    """))
                    .andExpect(status().isOk());

            assertThat(morningNotifications().stream()
                    .filter(n -> n.getStatus() == NotificationStatus.PENDING).toList())
                    .hasSize(2);
        }
    }

    @Nested
    @DisplayName("도시가 바뀌는 날 (v2.1 §3.6)")
    class CityChange {

        @Test
        @DisplayName("도시가 바뀌는 날은 전날 도시의 08:00에 보낸다 — 그 시각엔 아직 거기 있다")
        void sendsAtPreviousCityMorning() throws Exception {
            createTwoCityTrip();

            // 1/3이 방콕 첫날이다. 방콕 08:00(01:00Z)이 아니라 도쿄 08:00이어야 한다.
            assertThat(morningAt("2020-01-03")).isEqualTo(Instant.parse("2020-01-02T23:00:00Z"));
        }

        @Test
        @DisplayName("도시가 그대로인 날은 그날 도시의 08:00이다")
        void sendsAtOwnCityMorning() throws Exception {
            createTwoCityTrip();

            assertThat(morningAt("2020-01-02")).isEqualTo(Instant.parse("2020-01-01T23:00:00Z"));
        }

        @Test
        @DisplayName("첫날은 바뀐 것이 아니다 — 비교할 앞 날짜가 없다")
        void firstDayIsNotAChange() throws Exception {
            createTwoCityTrip();

            assertThat(morningAt("2020-01-01")).isEqualTo(Instant.parse("2019-12-31T23:00:00Z"));
        }

        @Test
        @DisplayName("기준 도시를 바꾸면 다음 날짜 요약까지 다시 잡힌다")
        void reschedulesFollowingDay() throws Exception {
            long tripId = createTwoCityTrip();
            // 1/3은 도쿄 → 방콕 이동일이라 도쿄 08:00에 잡혀 있다.
            assertThat(morningAt("2020-01-03")).isEqualTo(Instant.parse("2020-01-02T23:00:00Z"));

            // 1/2도 방콕으로 바꾸면 1/3은 더 이상 이동일이 아니다.
            changeBaseCity(dayIdOf(tripId, "2020-01-02"), bangkokCityId());

            // 바꾼 날짜가 아니라 그 다음 날짜의 발송 시각이 달라진다 — 방콕 08:00(01:00Z).
            assertThat(morningAt("2020-01-03")).isEqualTo(Instant.parse("2020-01-03T01:00:00Z"));
        }

        @Test
        @DisplayName("기준 도시를 바꿔도 요약이 두 벌 남지 않는다")
        void doesNotLeaveDuplicates() throws Exception {
            long tripId = createTwoCityTrip();

            changeBaseCity(dayIdOf(tripId, "2020-01-02"), bangkokCityId());

            assertThat(morningNotifications().stream()
                    .filter(n -> n.getStatus() == NotificationStatus.PENDING).toList())
                    .hasSize(3);
        }
    }

    @Nested
    @DisplayName("발송")
    class Dispatch {

        @Test
        @DisplayName("개수와 첫 일정을 담아 보낸다")
        void sendsSummary() throws Exception {
            long tripId = createTrip(true);
            addActivity(tripId, "숙소 체크아웃", "2020-01-01", "09:00");
            addActivity(tripId, "센소지", "2020-01-01", "11:00");

            dispatchService.dispatchDue();

            assertThat(stub.sent).anySatisfy(s -> assertThat(s.payload())
                    .contains("오늘 일정 2개 · 첫 일정 09:00 숙소 체크아웃"));
        }

        @Test
        @DisplayName("도시명을 앞에 붙인다 — 어느 도시의 하루인지가 첫 정보다")
        void prefixesCityName() throws Exception {
            long tripId = createTrip(true);
            addActivity(tripId, "숙소 체크아웃", "2020-01-01", "09:00");

            dispatchService.dispatchDue();

            assertThat(stub.sent).anySatisfy(s -> assertThat(s.payload())
                    .contains("도쿄 · 오늘 일정 1개 · 첫 일정 09:00 숙소 체크아웃"));
        }

        @Test
        @DisplayName("도시가 바뀌는 날은 `도쿄 → 방콕` 꼴로 말한다 — 그날 가장 큰 사건이다")
        void tellsCityMoveOnChangeDay() throws Exception {
            long tripId = createTwoCityTrip();
            addActivity(tripId, "공항 이동", "2020-01-03", "10:00");

            dispatchService.dispatchDue();

            assertThat(stub.sent).anySatisfy(s -> assertThat(s.payload())
                    .contains("도쿄 → 방콕 · 오늘 일정 1개"));
        }

        @Test
        @DisplayName("그날 일정이 없으면 보내지 않는다 — 예약은 접힌다")
        void skipsEmptyDay() throws Exception {
            createTrip(true);

            dispatchService.dispatchDue();

            assertThat(stub.sent).isEmpty();
            assertThat(morningNotifications())
                    .allSatisfy(n -> assertThat(n.getStatus())
                            .isEqualTo(NotificationStatus.CANCELED));
        }

        @Test
        @DisplayName("예약 뒤에 일정을 채운 날도 요약이 온다 — 0건 판정을 보낼 때 하는 이유다")
        void sendsForDayFilledAfterScheduling() throws Exception {
            long tripId = createTrip(true);
            // 여행을 만든 시점엔 1/2에 일정이 없었다.
            addActivity(tripId, "나중에 넣은 일정", "2020-01-02", "10:00");

            dispatchService.dispatchDue();

            assertThat(stub.sent).anySatisfy(s -> assertThat(s.payload())
                    .contains("오늘 일정 1개 · 첫 일정 10:00 나중에 넣은 일정"));
        }

        @Test
        @DisplayName("시각 없는 일정이 첫 번째면 제목만 붙인다")
        void handlesActivityWithoutTime() throws Exception {
            long tripId = createTrip(true);
            addActivity(tripId, "자유 일정", "2020-01-01", null);

            dispatchService.dispatchDue();

            assertThat(stub.sent).anySatisfy(s -> assertThat(s.payload())
                    .contains("오늘 일정 1개 · 첫 일정 자유 일정"));
        }

        @Test
        @DisplayName("탭하면 그날 보드로 간다 — 요약이 가리키는 건 하루 전체다")
        void linksToBoard() throws Exception {
            long tripId = createTrip(true);
            addActivity(tripId, "센소지", "2020-01-01", "09:00");

            dispatchService.dispatchDue();

            assertThat(stub.sent).anySatisfy(s -> assertThat(s.payload())
                    .contains("/travel/trips/" + tripId + "/board?date=2020-01-01"));
        }
    }

    private long tokyoCityId() throws Exception {
        return TravelCityFixture.createCity(mockMvc, authHeader, "도쿄", "Asia/Tokyo", "JPY");
    }

    /**
     * 도쿄(+9) 2일 → 방콕(+7) 1일.
     *
     * <p><b>타임존이 실제로 다른 두 도시</b>라야 "전날 도시 08:00"이 관찰된다 — 오사카·교토는
     * 둘 다 {@code Asia/Tokyo}여서 규칙이 맞든 틀리든 같은 순간이 나온다.
     */
    private long createTwoCityTrip() throws Exception {
        long tokyo = tokyoCityId();
        long bangkok = TravelCityFixture.createCity(
                mockMvc, authHeader, "방콕", "Asia/Bangkok", "THB");
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "동남아",
                                 "startDate": "2020-01-01", "endDate": "2020-01-03",
                                 "legs": [{"cityPlaceId": %d, "days": 2},
                                          {"cityPlaceId": %d, "days": 1}],
                                 "morningSummaryEnabled": true}
                                """.formatted(tokyo, bangkok)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.id")).longValue();
    }

    private long bangkokCityId() throws Exception {
        return TravelCityFixture.createCity(mockMvc, authHeader, "방콕2", "Asia/Bangkok", "THB");
    }

    /** 그 날짜의 대기 중 아침 요약 예약 시각. */
    private Instant morningAt(String date) {
        return morningNotifications().stream()
                .filter(n -> n.getStatus() == NotificationStatus.PENDING)
                .filter(n -> n.getTargetDate().toString().equals(date))
                .findFirst()
                .orElseThrow()
                .getScheduledAt();
    }

    private long dayIdOf(long tripId, String date) throws Exception {
        String body = mockMvc.perform(get("/api/travel/trips/" + tripId + "/days")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = com.jayway.jsonpath.JsonPath.read(
                body, "$.data[?(@.date=='" + date + "')].dayId");
        return ids.get(0).longValue();
    }

    private void changeBaseCity(long dayId, long cityPlaceId) throws Exception {
        mockMvc.perform(put("/api/travel/days/" + dayId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseCityPlaceId\": %d}".formatted(cityPlaceId)))
                .andExpect(status().isOk());
    }

}
