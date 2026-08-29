package ds.project.orino.planner.travel.push;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.push.entity.NotificationStatus;
import ds.project.orino.domain.planner.push.entity.NotificationType;
import ds.project.orino.domain.planner.push.entity.PushNotification;
import ds.project.orino.domain.planner.push.repository.PushNotificationRepository;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.repository.TravelPlaceRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 알림 예약과 재계산(§4.2).
 *
 * <p>가장 틀리기 쉬운 것은 <b>벽시계 → UTC 환산</b>이다. 일정은 여행 타임존의 벽시계 값으로
 * 저장되는데 스케줄러는 UTC로 돈다 — 여기서 어긋나면 알림이 몇 시간씩 밀린다.
 */
class NotificationScheduleTest extends ApiTestSupport {


    private static final BigDecimal SENSOJI_LAT = new BigDecimal("35.7147651");
    private static final BigDecimal SENSOJI_LNG = new BigDecimal("139.7966553");
    private static final BigDecimal SKYTREE_LAT = new BigDecimal("35.7100627");
    private static final BigDecimal SKYTREE_LNG = new BigDecimal("139.8107004");

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TravelPlaceRepository placeRepository;
    @Autowired
    private PushNotificationRepository notificationRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private Long memberId;
    private long tripId;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        memberId = memberRepository.findAll().get(0).getId();
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        tripId = createTrip("Asia/Tokyo");
    }

    private long createTrip(String timezone) throws Exception {
        long cityId = TravelCityFixture.createCity(mockMvc, authHeader, "도쿄", timezone, "JPY");
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "도쿄",
                                 "startDate": "2026-10-24", "endDate": "2026-10-27",
                                 %s,
                                 "defaultNotifyMinutes": 15}
                                """.formatted(TravelCityFixture.singleLeg(cityId, 4))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.id")).longValue();
    }

    /**
     * 이동시간 캐시(Redis)는 테스트 사이에 살아 있다. 좌표가 같으면 앞 테스트가 캐시해 둔
     * 이동시간이 새어 들어와, 스텁을 비워도 값이 나온다.
     */
    private static BigDecimal jitter(BigDecimal base) {
        int nudge = Math.abs(UUID.randomUUID().hashCode() % 9000) + 1000;
        return base.add(new BigDecimal("0.0000001").multiply(BigDecimal.valueOf(nudge)));
    }

    private Long placeAt(String name, BigDecimal lat, BigDecimal lng) {
        TravelPlace place = placeRepository.save(
                TravelPlace.fromGoogle(memberId, "g-" + UUID.randomUUID(), name));
        place.updateBasics(name, jitter(lat), jitter(lng), null, null);
        return placeRepository.saveAndFlush(place).getId();
    }

    /** 도시 식별자를 가진 장소. 도시 경계 판정은 좌표가 아니라 이 값으로만 한다(D-23). */
    private Long placeIn(String name, String cityPlaceRef, BigDecimal lat, BigDecimal lng) {
        TravelPlace place = placeRepository.findById(placeAt(name, lat, lng)).orElseThrow();
        place.updateCityInfo(cityPlaceRef, cityPlaceRef, "JP");
        return placeRepository.saveAndFlush(place).getId();
    }

    private long addActivity(String json) throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips/" + tripId + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 두 일정 사이 이동을 적어 둔다. 출발 알림은 여기 적힌 소요 시간으로만 선다(#1208). */
    private void saveMove(long from, long to, String fields) throws Exception {
        mockMvc.perform(put("/api/travel/trips/" + tripId + "/moves")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromActivityId": %d, "toActivityId": %d, %s}
                                """.formatted(from, to, fields)))
                .andExpect(status().isOk());
    }

    private List<PushNotification> pending() {
        return notificationRepository.findAllByMemberIdOrderByScheduledAtAsc(memberId).stream()
                .filter(n -> n.getStatus() == NotificationStatus.PENDING)
                .toList();
    }

    private static Instant tokyo(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZoneId.of("Asia/Tokyo")).toInstant();
    }

    @Nested
    @DisplayName("일정 알림")
    class ActivityNotification {

        @Test
        @DisplayName("시작시각 15분 전으로 예약한다 — 벽시계 시각을 여행 타임존으로 환산한다")
        void schedulesBeforeStart() throws Exception {
            addActivity("""
                    {"title": "센소지", "activityDate": "2026-10-24", "startTime": "09:00",
                     "notifyEnabled": true}
                    """);

            assertThat(pending()).singleElement().satisfies(n -> {
                assertThat(n.getType()).isEqualTo(NotificationType.ACTIVITY);
                // 도쿄 09:00 = UTC 00:00. 15분 전이면 UTC 2026-10-23T23:45.
                assertThat(n.getScheduledAt()).isEqualTo(tokyo("2026-10-24T08:45"));
                assertThat(n.getScheduledAt()).isEqualTo(Instant.parse("2026-10-23T23:45:00Z"));
            });
        }

        @Test
        @DisplayName("일정별 알림 시점이 여행 기본값을 이긴다")
        void activityMinutesOverrideTripDefault() throws Exception {
            addActivity("""
                    {"title": "센소지", "activityDate": "2026-10-24", "startTime": "09:00",
                     "notifyEnabled": true, "notifyMinutes": 60}
                    """);

            assertThat(pending()).singleElement().satisfies(n ->
                    assertThat(n.getScheduledAt()).isEqualTo(tokyo("2026-10-24T08:00")));
        }

        @Test
        @DisplayName("시각이 없으면 스위치가 켜져 있어도 만들지 않는다 — 언제 보낼지 정할 수 없다")
        void skipsWithoutStartTime() throws Exception {
            addActivity("""
                    {"title": "센소지", "activityDate": "2026-10-24", "notifyEnabled": true}
                    """);

            assertThat(pending()).isEmpty();
        }

        @Test
        @DisplayName("알림을 끈 일정은 만들지 않는다")
        void skipsWhenDisabled() throws Exception {
            addActivity("""
                    {"title": "센소지", "activityDate": "2026-10-24", "startTime": "09:00"}
                    """);

            assertThat(pending()).isEmpty();
        }
    }

    @Nested
    @DisplayName("출발 알림")
    class DepartureNotification {

        @Test
        @DisplayName("시작시각 − 적어 둔 소요 시간 − 5분")
        void schedulesBeforeDeparture() throws Exception {
            long from = addActivity("""
                    {"title": "센소지", "activityDate": "2026-10-24", "startTime": "09:00",
                     "placeId": %d}
                    """.formatted(placeAt("센소지", SENSOJI_LAT, SENSOJI_LNG)));
            long to = addActivity("""
                    {"title": "스카이트리", "activityDate": "2026-10-24", "startTime": "11:00",
                     "placeId": %d, "departureNotifyEnabled": true}
                    """.formatted(placeAt("스카이트리", SKYTREE_LAT, SKYTREE_LNG)));

            saveMove(from, to, "\"mode\": \"SUBWAY\", \"durationMinutes\": 12");

            // 11:00 − 12분 − 5분 = 10:43.
            assertThat(pending()).singleElement().satisfies(n -> {
                assertThat(n.getType()).isEqualTo(NotificationType.DEPARTURE);
                assertThat(n.getScheduledAt()).isEqualTo(tokyo("2026-10-24T10:43"));
            });
        }

        @Test
        @DisplayName("직전 장소 있는 일정이 없으면 만들지 않는다 — 어디서 출발하는지 모른다")
        void skipsWithoutPreviousPlace() throws Exception {
            addActivity("""
                    {"title": "스카이트리", "activityDate": "2026-10-24", "startTime": "11:00",
                     "placeId": %d, "departureNotifyEnabled": true}
                    """.formatted(placeAt("스카이트리", SKYTREE_LAT, SKYTREE_LNG)));

            assertThat(pending()).isEmpty();
        }

        @Test
        @DisplayName("소요 시간을 아직 안 적었으면 만들지 않는다 — 지어내지 않는다")
        void skipsWhenDurationUnknown() throws Exception {
            long from = addActivity("""
                    {"title": "센소지", "activityDate": "2026-10-24", "startTime": "09:00",
                     "placeId": %d}
                    """.formatted(placeAt("센소지", SENSOJI_LAT, SENSOJI_LNG)));
            long to = addActivity("""
                    {"title": "스카이트리", "activityDate": "2026-10-24", "startTime": "11:00",
                     "placeId": %d, "departureNotifyEnabled": true}
                    """.formatted(placeAt("스카이트리", SKYTREE_LAT, SKYTREE_LNG)));

            // 수단만 정하고 시간은 나중에 확인하는 상태.
            saveMove(from, to, "\"mode\": \"SUBWAY\"");

            assertThat(pending()).isEmpty();
        }

        @Test
        @DisplayName("도시를 넘는 이동에도 만든다 — 적어 두면 언제 나서야 할지 알 수 있다 (#1208)")
        void schedulesAcrossCityBoundary() throws Exception {
            // 자동 계산 시절에는 도시를 넘으면 소요 시간을 못 구해 알림이 서지 않았다.
            // 신칸센 구간이야말로 "언제 나서야 하는가"가 가장 중요한 이동이다.
            long from = addActivity("""
                    {"title": "오사카 가게", "activityDate": "2026-10-24", "startTime": "09:00",
                     "placeId": %d}
                    """.formatted(placeIn("오사카 가게", "ChIJ_osaka", SENSOJI_LAT, SENSOJI_LNG)));
            long to = addActivity("""
                    {"title": "교토 가게", "activityDate": "2026-10-24", "startTime": "11:00",
                     "placeId": %d, "departureNotifyEnabled": true}
                    """.formatted(placeIn("교토 가게", "ChIJ_kyoto", SKYTREE_LAT, SKYTREE_LNG)));

            saveMove(from, to, """
                    "mode": "TRAIN", "name": "특급 하루카", "durationMinutes": 75
                    """);

            assertThat(pending()).singleElement().satisfies(n -> {
                assertThat(n.getType()).isEqualTo(NotificationType.DEPARTURE);
                assertThat(n.getScheduledAt()).isEqualTo(tokyo("2026-10-24T09:40"));
            });
        }

        @Test
        @DisplayName("이동을 지우면 출발 알림도 사라진다 — 지워진 값으로 울리면 안 된다")
        void cancelsWhenMoveDeleted() throws Exception {
            long from = addActivity("""
                    {"title": "센소지", "activityDate": "2026-10-24", "startTime": "09:00",
                     "placeId": %d}
                    """.formatted(placeAt("센소지", SENSOJI_LAT, SENSOJI_LNG)));
            long to = addActivity("""
                    {"title": "스카이트리", "activityDate": "2026-10-24", "startTime": "11:00",
                     "placeId": %d, "departureNotifyEnabled": true}
                    """.formatted(placeAt("스카이트리", SKYTREE_LAT, SKYTREE_LNG)));
            saveMove(from, to, "\"mode\": \"SUBWAY\", \"durationMinutes\": 12");
            assertThat(pending()).hasSize(1);

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete("/api/travel/trips/" + tripId + "/moves")
                            .param("from", String.valueOf(from))
                            .param("to", String.valueOf(to))
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            assertThat(pending()).isEmpty();
        }

        @Test
        @DisplayName("소요 시간을 고치면 알림 시각도 따라 바뀐다")
        void reschedulesWhenDurationChanges() throws Exception {
            long from = addActivity("""
                    {"title": "센소지", "activityDate": "2026-10-24", "startTime": "09:00",
                     "placeId": %d}
                    """.formatted(placeAt("센소지", SENSOJI_LAT, SENSOJI_LNG)));
            long to = addActivity("""
                    {"title": "스카이트리", "activityDate": "2026-10-24", "startTime": "11:00",
                     "placeId": %d, "departureNotifyEnabled": true}
                    """.formatted(placeAt("스카이트리", SKYTREE_LAT, SKYTREE_LNG)));

            saveMove(from, to, "\"mode\": \"WALK\", \"durationMinutes\": 40");
            saveMove(from, to, "\"mode\": \"SUBWAY\", \"durationMinutes\": 12");

            assertThat(pending()).singleElement().satisfies(n ->
                    assertThat(n.getScheduledAt()).isEqualTo(tokyo("2026-10-24T10:43")));
        }

    }

    @Nested
    @DisplayName("재계산 (§4.2)")
    class Recalculation {

        @Test
        @DisplayName("시각을 바꾸면 옛 예약은 취소되고 새로 잡힌다")
        void reschedulesOnTimeChange() throws Exception {
            long activityId = addActivity("""
                    {"title": "센소지", "activityDate": "2026-10-24", "startTime": "09:00",
                     "notifyEnabled": true}
                    """);

            mockMvc.perform(put("/api/travel/activities/" + activityId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "센소지", "activityDate": "2026-10-24",
                                     "startTime": "14:00", "notifyEnabled": true}
                                    """))
                    .andExpect(status().isOk());

            assertThat(pending()).singleElement().satisfies(n ->
                    assertThat(n.getScheduledAt()).isEqualTo(tokyo("2026-10-24T13:45")));
            // 지우지 않고 남긴다 — 왜 그 시각에 갔는지(혹은 안 갔는지) 추적할 수 있어야 한다.
            assertThat(notificationRepository.findAll())
                    .anySatisfy(n -> assertThat(n.getStatus())
                            .isEqualTo(NotificationStatus.CANCELED));
        }

        @Test
        @DisplayName("타임존을 바꾸면 벽시계 시각은 그대로고 알림 시각만 바뀐다")
        void reschedulesOnTimezoneChange() throws Exception {
            addActivity("""
                    {"title": "센소지", "activityDate": "2026-10-24", "startTime": "09:00",
                     "notifyEnabled": true}
                    """);
            assertThat(pending()).singleElement().satisfies(n ->
                    assertThat(n.getScheduledAt()).isEqualTo(Instant.parse("2026-10-23T23:45:00Z")));

            // v2.1에서 타임존을 바꾸는 길은 기준 도시를 바꾸는 것 하나뿐이다.
            long bangkok = TravelCityFixture.createCity(mockMvc, authHeader, "방콕",
                    "Asia/Bangkok", "THB");
            mockMvc.perform(put("/api/travel/trips/" + tripId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "도쿄",
                                     "startDate": "2026-10-24", "endDate": "2026-10-27",
                                     %s}
                                    """.formatted(TravelCityFixture.singleLeg(bangkok, 4))))
                    .andExpect(status().isOk());

            // 09:00은 어디서든 09:00이지만, 방콕(UTC+7) 09:00은 도쿄(UTC+9) 09:00보다 2시간 늦다.
            // (서울로 바꾸면 아무것도 안 바뀐다 — 도쿄와 같은 UTC+9다.)
            assertThat(pending()).singleElement().satisfies(n ->
                    assertThat(n.getScheduledAt()).isEqualTo(Instant.parse("2026-10-24T01:45:00Z")));
        }

        @Test
        @DisplayName("일정을 지우면 예약도 접힌다")
        void cancelsOnDelete() throws Exception {
            long activityId = addActivity("""
                    {"title": "센소지", "activityDate": "2026-10-24", "startTime": "09:00",
                     "notifyEnabled": true}
                    """);
            assertThat(pending()).hasSize(1);

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete("/api/travel/activities/" + activityId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            assertThat(pending()).isEmpty();
        }

        @Test
        @DisplayName("보관함으로 옮기면 예약이 접힌다 — 날짜가 없으면 보낼 시각도 없다")
        void cancelsOnArchive() throws Exception {
            long activityId = addActivity("""
                    {"title": "센소지", "activityDate": "2026-10-24", "startTime": "09:00",
                     "notifyEnabled": true}
                    """);

            mockMvc.perform(put("/api/travel/activities/" + activityId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "센소지", "activityDate": null,
                                     "startTime": "09:00", "notifyEnabled": true}
                                    """))
                    .andExpect(status().isOk());

            assertThat(pending()).isEmpty();
        }

        @Test
        @DisplayName("알림을 끄면 예약이 사라진다")
        void cancelsWhenDisabled() throws Exception {
            long activityId = addActivity("""
                    {"title": "센소지", "activityDate": "2026-10-24", "startTime": "09:00",
                     "notifyEnabled": true}
                    """);

            mockMvc.perform(put("/api/travel/activities/" + activityId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "센소지", "activityDate": "2026-10-24",
                                     "startTime": "09:00", "notifyEnabled": false}
                                    """))
                    .andExpect(status().isOk());

            assertThat(pending()).isEmpty();
        }

        @Test
        @DisplayName("순서를 바꾸면 출발 알림이 다시 잡힌다 — 어느 구간이 이어지는지가 달라진다")
        void reschedulesOnReorder() throws Exception {
            long first = addActivity("""
                    {"title": "센소지", "activityDate": "2026-10-24", "startTime": "09:00",
                     "placeId": %d, "departureNotifyEnabled": true}
                    """.formatted(placeAt("센소지", SENSOJI_LAT, SENSOJI_LNG)));
            long second = addActivity("""
                    {"title": "스카이트리", "activityDate": "2026-10-24", "startTime": "11:00",
                     "placeId": %d, "departureNotifyEnabled": true}
                    """.formatted(placeAt("스카이트리", SKYTREE_LAT, SKYTREE_LNG)));

            // 양방향을 다 적어 둔다 — 어느 쪽으로 이어지든 소요 시간을 아는 상태로 만든다.
            saveMove(first, second, "\"mode\": \"SUBWAY\", \"durationMinutes\": 12");
            saveMove(second, first, "\"mode\": \"SUBWAY\", \"durationMinutes\": 12");

            // 처음엔 두 번째 일정에만 출발 알림이 붙는다(첫 일정은 앞이 없다).
            assertThat(pending()).singleElement().satisfies(n ->
                    assertThat(n.getActivityId()).isEqualTo(second));

            mockMvc.perform(put("/api/travel/trips/" + tripId + "/activities/order")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"moves": [{"date": "2026-10-24",
                                                "activityIds": [%d, %d]}]}
                                    """.formatted(second, first)))
                    .andExpect(status().isOk());

            // 뒤집혔으니 이제 첫 일정 쪽에 붙는다.
            assertThat(pending()).singleElement().satisfies(n ->
                    assertThat(n.getActivityId()).isEqualTo(first));
        }
    }

    @Nested
    @DisplayName("날짜 전체 재계산")
    class WholeDay {

        @Test
        @DisplayName("앞에 일정이 끼면 뒤 일정의 출발 시각도 다시 잡는다")
        void recalculatesFollowingActivities() throws Exception {
            addActivity("""
                    {"title": "센소지", "activityDate": "2026-10-24", "startTime": "09:00",
                     "placeId": %d}
                    """.formatted(placeAt("센소지", SENSOJI_LAT, SENSOJI_LNG)));
            addActivity("""
                    {"title": "스카이트리", "activityDate": "2026-10-24", "startTime": "11:00",
                     "placeId": %d, "departureNotifyEnabled": true}
                    """.formatted(placeAt("스카이트리", SKYTREE_LAT, SKYTREE_LNG)));

            long before = pending().size();
            // 장소 없는 일정을 사이에 넣어도 이동시간은 그대로라 개수가 유지돼야 한다.
            addActivity("""
                    {"title": "점심", "activityDate": "2026-10-24", "startTime": "10:00"}
                    """);

            assertThat(pending()).hasSize((int) before);
        }
    }
}
