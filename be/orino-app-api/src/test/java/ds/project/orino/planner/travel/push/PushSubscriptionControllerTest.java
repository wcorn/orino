package ds.project.orino.planner.travel.push;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.push.entity.PushSubscription;
import ds.project.orino.domain.planner.push.repository.PushSubscriptionRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 웹푸시 구독 등록·해지. */
class PushSubscriptionControllerTest extends ApiTestSupport {

    /** 실제 FCM 엔드포인트만큼 길다 — 이 길이가 유니크 인덱스를 해시로 거는 이유다. */
    private static final String ENDPOINT =
            "https://fcm.googleapis.com/fcm/send/dQw4w9WgXcQ:APA91bH"
                    + "Zx7vK2mNqL8pR3sT5uV6wX9yZ0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wX2yZ3aB4cD5eF6gH7i"
                    + "J8kL9mN0oP1qR2sT3uV4wX5yZ6aB7cD8eF9gH0iJ1kL2mN3oP4qR5sT6uV7wX8yZ9aB0cD1eF2gH3i";

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PushSubscriptionRepository subscriptionRepository;
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

    /** 브라우저의 {@code PushSubscription.toJSON()}과 같은 모양 — keys가 중첩된다. */
    private static String body(String endpoint, String p256dh, String auth) {
        return """
                {"endpoint": "%s", "keys": {"p256dh": "%s", "auth": "%s"},
                 "userAgent": "Mozilla/5.0 (Linux; Android 14)"}
                """.formatted(endpoint, p256dh, auth);
    }

    /** 해지는 주소만 보낸다 — 기기가 키를 이미 버렸을 수 있다. */
    private static String unsubscribeBody(String endpoint) {
        return """
                {"endpoint": "%s"}
                """.formatted(endpoint);
    }

    private org.springframework.test.web.servlet.ResultActions subscribe(String header,
                                                                        String content)
            throws Exception {
        return mockMvc.perform(post("/api/travel/push/subscriptions")
                .header(HttpHeaders.AUTHORIZATION, header)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content));
    }

    @Nested
    @DisplayName("공개키")
    class PublicKey {

        @Test
        @DisplayName("구독에 쓸 공개키를 내려준다 — FE에 박아두지 않고 한 곳에서 관리한다")
        void returnsPublicKey() throws Exception {
            mockMvc.perform(get("/api/travel/push/public-key")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    // 테스트 환경엔 키가 없다. 오류가 아니라 "아직 없음"이다.
                    .andExpect(jsonPath("$.data").exists());
        }
    }

    @Nested
    @DisplayName("등록")
    class Subscribe {

        @Test
        @DisplayName("구독을 저장한다")
        void savesSubscription() throws Exception {
            subscribe(authHeader, body(ENDPOINT, "BExample_p256dh", "BExample_auth"))
                    .andExpect(status().isOk());

            assertThat(subscriptionRepository.findAll()).singleElement()
                    .satisfies(saved -> {
                        assertThat(saved.getEndpoint()).isEqualTo(ENDPOINT);
                        assertThat(saved.getP256dh()).isEqualTo("BExample_p256dh");
                        assertThat(saved.getEndpointHash()).hasSize(64);
                    });
        }

        @Test
        @DisplayName("같은 기기가 다시 구독하면 새 행이 아니라 갱신이다")
        void refreshesInsteadOfDuplicating() throws Exception {
            subscribe(authHeader, body(ENDPOINT, "old_p256dh", "old_auth"))
                    .andExpect(status().isOk());
            // 브라우저는 재구독 때 키를 새로 만들어 주기도 한다.
            subscribe(authHeader, body(ENDPOINT, "new_p256dh", "new_auth"))
                    .andExpect(status().isOk());

            assertThat(subscriptionRepository.findAll()).singleElement()
                    .satisfies(saved -> {
                        // 옛 키를 두면 그 키로 암호화한 알림을 기기가 못 푼다.
                        assertThat(saved.getP256dh()).isEqualTo("new_p256dh");
                        assertThat(saved.getAuth()).isEqualTo("new_auth");
                    });
        }

        @Test
        @DisplayName("기기가 다르면 따로 저장된다 — 한 사람이 여러 기기를 쓴다")
        void keepsSubscriptionsPerDevice() throws Exception {
            subscribe(authHeader, body(ENDPOINT, "a", "a")).andExpect(status().isOk());
            subscribe(authHeader, body(ENDPOINT + "-tablet", "b", "b"))
                    .andExpect(status().isOk());

            assertThat(subscriptionRepository.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("keys가 없으면 400 — 브라우저 구독 객체를 그대로 보내야 한다")
        void rejectsMissingKeys() throws Exception {
            subscribe(authHeader, """
                    {"endpoint": "%s"}
                    """.formatted(ENDPOINT))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("구독 주소가 없으면 400")
        void rejectsBlankEndpoint() throws Exception {
            subscribe(authHeader, body("", "a", "a")).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("긴 User-Agent도 저장된다 — 컬럼을 넘기면 저장 자체가 실패한다")
        void truncatesLongUserAgent() throws Exception {
            String longAgent = "A".repeat(900);
            mockMvc.perform(post("/api/travel/push/subscriptions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"endpoint": "%s", "keys": {"p256dh": "a", "auth": "a"},
                                     "userAgent": "%s"}
                                    """.formatted(ENDPOINT, longAgent)))
                    .andExpect(status().isOk());

            assertThat(subscriptionRepository.findAll()).singleElement()
                    .satisfies(saved -> assertThat(saved.getUserAgent()).hasSize(500));
        }
    }

    @Nested
    @DisplayName("해지")
    class Unsubscribe {

        @Test
        @DisplayName("그 기기의 구독만 지운다")
        void removesSubscription() throws Exception {
            subscribe(authHeader, body(ENDPOINT, "a", "a")).andExpect(status().isOk());
            subscribe(authHeader, body(ENDPOINT + "-tablet", "b", "b"))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/travel/push/subscriptions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(unsubscribeBody(ENDPOINT)))
                    .andExpect(status().isOk());

            assertThat(subscriptionRepository.findAll()).singleElement()
                    .satisfies(left -> assertThat(left.getEndpoint()).endsWith("-tablet"));
        }

        @Test
        @DisplayName("남의 구독은 지우지 못한다")
        void cannotRemoveOthersSubscription() throws Exception {
            subscribe(authHeader, body(ENDPOINT, "a", "a")).andExpect(status().isOk());

            mockMvc.perform(delete("/api/travel/push/subscriptions")
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(unsubscribeBody(ENDPOINT)))
                    .andExpect(status().isOk());

            assertThat(subscriptionRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("없는 구독을 해지해도 조용히 넘어간다 — 결과가 같다")
        void ignoresUnknownSubscription() throws Exception {
            mockMvc.perform(delete("/api/travel/push/subscriptions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(unsubscribeBody(ENDPOINT)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("endpoint 해시")
    class Hashing {

        @Test
        @DisplayName("같은 주소는 같은 해시, 다르면 다른 해시")
        void isDeterministic() {
            assertThat(PushSubscription.hash(ENDPOINT))
                    .isEqualTo(PushSubscription.hash(ENDPOINT))
                    .isNotEqualTo(PushSubscription.hash(ENDPOINT + "x"))
                    .hasSize(64);
        }
    }
}
