package ds.project.orino.planner.travel.push;

import com.jayway.jsonpath.JsonPath;
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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 준비 알림(v2.2 §14 · 아키텍처 §11.4).
 *
 * <p>여행당 <b>하나</b>다. 출발 전날 현지 09:00에, 남은 항목이 있을 때만.
 *
 * <p>아침 요약과 닮았지만 결정적으로 다른 점이 하나 있다 — <b>가는 날짜가 여행 기간 밖</b>
 * (출발 전날)이다. 그 날짜에는 기준 도시가 없으므로 시계는 첫날 도시에서 온다. 그래서
 * 날짜 집합으로 찾는 재계산 경로에도 걸리지 않고, 자기 경로를 따로 탄다.
 *
 * <p>시각을 못박는다(도쿄 2026-01-15 11:00). 발송을 보려면 <b>예약 시각은 지났는데 아직
 * 출발하지는 않은</b> 순간이 필요한데, 그건 실시각으로는 하루에 열다섯 시간뿐인 창이다.
 */
@FixedClock
class PrepReminderTest extends ApiTestSupport {

    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/prep-device";

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
    private long tokyo;
    private long honolulu;

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

        tokyo = TravelCityFixture.createCity(mockMvc, authHeader, "도쿄", "Asia/Tokyo", "JPY");
        honolulu = TravelCityFixture.createCity(mockMvc, authHeader, "호놀룰루",
                "Pacific/Honolulu", "USD");
    }

    @Nested
    @DisplayName("예약")
    class Scheduling {

        @Test
        @DisplayName("출발 전날 09:00을 첫날 기준 도시의 시계로 잡는다")
        void schedulesTheDayBeforeAtNine() throws Exception {
            createTrip("2026-10-24", "2026-10-27", tokyo);

            // 도쿄 10/23 09:00 = UTC 10/23 00:00.
            assertThat(prepReminders()).singleElement().satisfies(n -> {
                assertThat(n.getType()).isEqualTo(NotificationType.PREP_REMINDER);
                assertThat(n.getScheduledAt())
                        .isEqualTo(Instant.parse("2026-10-23T00:00:00Z"));
                // 일정에 매달려 있지 않다. 가는 날짜는 여행 기간 밖이다.
                assertThat(n.getActivityId()).isNull();
                assertThat(n.getTargetDate()).isEqualTo("2026-10-23");
            });
        }

        @Test
        @DisplayName("여행당 하나뿐이다 — D-7·D-3으로 나눠 보내지 않는다")
        void onlyOnePerTrip() throws Exception {
            createTrip("2026-10-24", "2026-10-27", tokyo);

            assertThat(prepReminders()).hasSize(1);
        }

        @Test
        @DisplayName("준비를 하나도 안 적었어도 잡는다 — 0건 판정은 보낼 때 한다")
        void schedulesEvenWithoutItems() throws Exception {
            createTrip("2026-10-24", "2026-10-27", tokyo);

            assertThat(prepReminders()).hasSize(1);
        }

        @Test
        @DisplayName("기간을 바꾸면 옛 예약은 CANCELED되고 새 시각으로 다시 만들어진다")
        void reschedulesOnPeriodChange() throws Exception {
            long tripId = createTrip("2026-10-24", "2026-10-27", tokyo);

            updateTrip(tripId, "2026-10-20", "2026-10-23", tokyo);

            assertThat(prepReminders()).singleElement().satisfies(n ->
                    // 도쿄 10/19 09:00 = UTC 10/19 00:00.
                    assertThat(n.getScheduledAt())
                            .isEqualTo(Instant.parse("2026-10-19T00:00:00Z")));
            assertThat(canceledPrepReminders()).hasSize(1);
        }

        @Test
        @DisplayName("첫날 기준 도시를 바꾸면 발송 시각이 그 도시의 09:00으로 옮겨진다")
        void reschedulesWhenFirstCityChanges() throws Exception {
            long tripId = createTrip("2026-10-24", "2026-10-27", tokyo);
            long firstDayId = dayIdOf(tripId, 0);

            changeBaseCity(firstDayId, honolulu);

            // 호놀룰루(UTC−10) 10/23 09:00 = UTC 10/23 19:00.
            assertThat(prepReminders()).singleElement().satisfies(n ->
                    assertThat(n.getScheduledAt())
                            .isEqualTo(Instant.parse("2026-10-23T19:00:00Z")));
        }

        @Test
        @DisplayName("첫날이 아닌 날짜의 도시를 바꾸면 준비 알림은 그대로다")
        void ignoresOtherDaysCityChange() throws Exception {
            long tripId = createTrip("2026-10-24", "2026-10-27", tokyo);
            Instant before = prepReminders().get(0).getScheduledAt();

            changeBaseCity(dayIdOf(tripId, 2), honolulu);

            // 준비 알림의 시계는 첫날 도시 하나뿐이다.
            assertThat(prepReminders()).singleElement().satisfies(n ->
                    assertThat(n.getScheduledAt()).isEqualTo(before));
            assertThat(canceledPrepReminders()).isEmpty();
        }
    }

    @Nested
    @DisplayName("발송")
    class Dispatch {

        @Test
        @DisplayName("남은 개수를 보낼 때 다시 세어 본문에 넣는다")
        void countsRemainingAtSendTime() throws Exception {
            // 내일(도쿄 1/16) 출발. 예약은 1/15 09:00이라 지금(11:00)은 이미 지났다.
            long tripId = createTrip("2026-01-16", "2026-01-18", tokyo);
            addPrepItem(tripId, "여권");
            long packed = addPrepItem(tripId, "멀티어댑터");
            addPrepItem(tripId, "환전");
            // 예약한 뒤에 체크한다 — 저장해 뒀다면 3개라고 나갔을 자리다.
            checkPrepItem(packed);

            dispatchService.dispatchDue();

            assertThat(stub.sent).singleElement().satisfies(sent -> {
                assertThat(sent.payload()).contains("\"title\":\"내일 출발\"");
                assertThat(sent.payload()).contains("준비 2개 남았어요");
                assertThat(sent.payload())
                        .contains("/travel/trips/%d/prep".formatted(tripId));
            });
        }

        @Test
        @DisplayName("남은 게 0개면 보내지 않고 예약을 접는다")
        void skipsWhenNothingLeft() throws Exception {
            long tripId = createTrip("2026-01-16", "2026-01-18", tokyo);
            checkPrepItem(addPrepItem(tripId, "여권"));

            dispatchService.dispatchDue();

            assertThat(stub.sent).isEmpty();
            assertThat(canceledPrepReminders()).hasSize(1);
        }

        @Test
        @DisplayName("적은 게 하나도 없어도 보내지 않는다")
        void skipsWhenNothingWritten() throws Exception {
            createTrip("2026-01-16", "2026-01-18", tokyo);

            dispatchService.dispatchDue();

            assertThat(stub.sent).isEmpty();
        }

        @Test
        @DisplayName("이미 출발했으면 보내지 않는다 — 「내일 출발」이 거짓이 된다")
        void skipsAfterDeparture() throws Exception {
            // 어제(도쿄 1/14) 출발한 여행. 예약 시각은 지났지만 「내일 출발」은 이미 거짓이다.
            long tripId = createTrip("2026-01-14", "2026-01-16", tokyo);
            addPrepItem(tripId, "여권");

            dispatchService.dispatchDue();

            assertThat(stub.sent).isEmpty();
            assertThat(canceledPrepReminders()).hasSize(1);
        }
    }

    // ---------------- helpers ----------------

    private long createTrip(String start, String end, long cityId) throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "일본 가을", "startDate": "%s", "endDate": "%s", %s}
                                """.formatted(start, end,
                                TravelCityFixture.singleLeg(cityId, days(start, end)))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private void updateTrip(long tripId, String start, String end, long cityId)
            throws Exception {
        mockMvc.perform(put("/api/travel/trips/" + tripId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "일본 가을", "startDate": "%s", "endDate": "%s", %s}
                                """.formatted(start, end,
                                TravelCityFixture.singleLeg(cityId, days(start, end)))))
                .andExpect(status().isOk());
    }

    private static int days(String start, String end) {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.parse(start), java.time.LocalDate.parse(end)) + 1;
    }

    private long dayIdOf(long tripId, int index) throws Exception {
        String body = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/travel/trips/" + tripId + "/days")
                                .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data[%d].dayId".formatted(index))).longValue();
    }

    private void changeBaseCity(long dayId, long cityPlaceId) throws Exception {
        mockMvc.perform(put("/api/travel/days/" + dayId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseCityPlaceId\": %d}".formatted(cityPlaceId)))
                .andExpect(status().isOk());
    }

    private long addPrepItem(long tripId, String title) throws Exception {
        String body = mockMvc.perform(
                        post("/api/travel/trips/" + tripId + "/prep/items")
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\": \"%s\"}".formatted(title)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.item.id")).longValue();
    }

    private void checkPrepItem(long itemId) throws Exception {
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .patch("/api/travel/prep/items/" + itemId)
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"done\": true}"))
                .andExpect(status().isOk());
    }

    private List<PushNotification> prepReminders() {
        return remindersWith(NotificationStatus.PENDING);
    }

    private List<PushNotification> canceledPrepReminders() {
        return remindersWith(NotificationStatus.CANCELED);
    }

    private List<PushNotification> remindersWith(NotificationStatus status) {
        return notificationRepository.findAllByMemberIdOrderByScheduledAtAsc(memberId).stream()
                .filter(n -> n.getType() == NotificationType.PREP_REMINDER)
                .filter(n -> n.getStatus() == status)
                .toList();
    }
}
