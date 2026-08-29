package ds.project.orino.planner.travel.push;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.push.entity.NotificationStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 발송(§6).
 *
 * <p>핵심은 <b>제목을 언제 읽느냐</b>다. 예약할 때 저장해두면 제목만 고친 일정이 옛 제목으로
 * 알림된다 — §4.2 재계산 트리거에 "제목 변경"이 없기 때문이다.
 */
class NotificationDispatchTest extends ApiTestSupport {


    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/device-a";

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
    private long tripId;

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

        long cityId = TravelCityFixture.createCity(mockMvc, authHeader, "도쿄",
                "Asia/Tokyo", "JPY");
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "도쿄",
                                 "startDate": "2020-01-01", "endDate": "2020-01-02",
                                 %s}
                                """.formatted(TravelCityFixture.singleLeg(cityId, 2))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        tripId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 과거 날짜로 만든다 — 예약 시각이 이미 지나 있어야 바로 발송 대상이 된다. */
    private long addPastActivity(String title) throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "activityDate": "2020-01-01",
                                 "startTime": "09:00", "notifyEnabled": true}
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.id")).longValue();
    }

    @Nested
    @DisplayName("보낼 때가 된 알림")
    class Due {

        @Test
        @DisplayName("보내고 SENT로 남긴다")
        void sendsAndMarks() throws Exception {
            addPastActivity("센소지");

            assertThat(dispatchService.dispatchDue()).isEqualTo(1);
            assertThat(stub.sent).singleElement()
                    .satisfies(s -> assertThat(s.endpoint()).isEqualTo(ENDPOINT));
            assertThat(notificationRepository.findAll()).singleElement()
                    .satisfies(n -> assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT));
        }

        @Test
        @DisplayName("제목은 발송 시점에 읽는다 — 예약 때 저장하면 옛 제목이 나간다")
        void readsTitleAtSendTime() throws Exception {
            long activityId = addPastActivity("옛 제목");

            // 제목 변경은 §4.2 재계산 트리거가 아니다 — 예약은 그대로 남는다.
            mockMvc.perform(put("/api/travel/activities/" + activityId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "새 제목", "activityDate": "2020-01-01",
                                     "startTime": "09:00", "notifyEnabled": true}
                                    """))
                    .andExpect(status().isOk());

            dispatchService.dispatchDue();

            assertThat(stub.sent).isNotEmpty();
            assertThat(stub.sent.get(0).payload()).contains("새 제목").doesNotContain("옛 제목");
        }

        @Test
        @DisplayName("탭했을 때 갈 곳을 함께 싣는다 — SW가 이 값으로 창을 옮긴다")
        void carriesDeepLink() throws Exception {
            long activityId = addPastActivity("센소지");

            dispatchService.dispatchDue();

            assertThat(stub.sent.get(0).payload())
                    .contains("/travel/activities/" + activityId);
        }

        @Test
        @DisplayName("아직 시각이 안 된 알림은 건드리지 않는다")
        void ignoresFuture() throws Exception {
            addPastActivity("지금 갈 것");

            // 먼 미래 여행을 따로 만들어 예약만 잡아 둔다.
            long futureCity = TravelCityFixture.createCity(mockMvc, authHeader, "도쿄",
                    "Asia/Tokyo", "JPY");
            String future = mockMvc.perform(post("/api/travel/trips")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "먼 여행",
                                     "startDate": "2090-01-01", "endDate": "2090-01-02",
                                     %s}
                                    """.formatted(
                                            TravelCityFixture.singleLeg(futureCity, 2))))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            long futureTripId =
                    ((Number) com.jayway.jsonpath.JsonPath.read(future, "$.data.id")).longValue();
            mockMvc.perform(post("/api/travel/trips/" + futureTripId + "/activities")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "나중에 갈 것", "activityDate": "2090-01-01",
                                     "startTime": "09:00", "notifyEnabled": true}
                                    """))
                    .andExpect(status().isOk());

            assertThat(notificationRepository.findAll()).hasSize(2);
            // 지난 것만 나간다.
            assertThat(dispatchService.dispatchDue()).isEqualTo(1);
            assertThat(stub.sent).singleElement()
                    .satisfies(sent -> assertThat(sent.payload()).contains("지금 갈 것"));
        }

        @Test
        @DisplayName("두 번 돌려도 다시 보내지 않는다 — SENT는 폴링에서 빠진다")
        void doesNotResend() throws Exception {
            addPastActivity("센소지");

            assertThat(dispatchService.dispatchDue()).isEqualTo(1);
            assertThat(dispatchService.dispatchDue()).isZero();
            assertThat(stub.sent).hasSize(1);
        }
    }

    @Nested
    @DisplayName("실패")
    class Failure {

        @Test
        @DisplayName("410이면 구독을 지운다 — 죽은 주소에 계속 보내면 비용만 든다")
        void removesGoneSubscription() throws Exception {
            stub.nextResult = WebPushSender.Result.gone("HTTP 410");
            addPastActivity("센소지");

            dispatchService.dispatchDue();

            assertThat(subscriptionRepository.findAll()).isEmpty();
            assertThat(notificationRepository.findAll()).singleElement()
                    .satisfies(n -> assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED));
        }

        @Test
        @DisplayName("일시적 실패는 구독을 남긴다 — 잘못 지우면 다시 구독해야 알림이 온다")
        void keepsSubscriptionOnTransientFailure() throws Exception {
            stub.nextResult = WebPushSender.Result.failed("HTTP 500");
            addPastActivity("센소지");

            dispatchService.dispatchDue();

            assertThat(subscriptionRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("구독이 없으면 FAILED로 남긴다")
        void failsWithoutSubscription() throws Exception {
            subscriptionRepository.deleteAll();
            addPastActivity("센소지");

            assertThat(dispatchService.dispatchDue()).isZero();
            assertThat(notificationRepository.findAll()).singleElement()
                    .satisfies(n -> assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED));
        }
    }

    @Nested
    @DisplayName("즉시 발송 (S-09)")
    class TestSend {

        @Test
        @DisplayName("구독한 기기로 곧바로 보낸다 — 실기기 검증의 시작점이다")
        void sendsImmediately() throws Exception {
            mockMvc.perform(post("/api/travel/push/test")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(1));

            assertThat(stub.sent).singleElement()
                    .satisfies(s -> assertThat(s.payload()).contains("알림 테스트"));
        }

        @Test
        @DisplayName("구독이 없으면 0을 준다 — 오류가 아니라 '보낼 곳이 없음'이다")
        void reportsZeroWithoutSubscription() throws Exception {
            subscriptionRepository.deleteAll();

            mockMvc.perform(post("/api/travel/push/test")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(0));
        }
    }
}
