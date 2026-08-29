package ds.project.orino.planner.shortlink;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.TestClocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 비밀번호 보호(#1244). <b>404 단일 원칙의 명시적 예외다</b>(명세 §10 · D-10).
 *
 * <p>이 테스트가 고정하는 것은 예외의 <b>경계</b>다 — 확인 화면이 뜨는 것은 비밀번호를 건
 * 링크에서만이고, 없는 슬러그는 여전히 404이며, 통과해도 쿠키가 생기지 않는다.
 */
// 시도 제한은 "분"으로 창을 나눈다. 실시각으로 돌리면 10회를 세는 도중 분이 넘어가 카운터가
// 리셋되고, 느린 CI에서만 가끔 깨진다(실제로 깨졌다). 시계를 고정해 창이 넘어가지 않게 한다 —
// 이미 여러 테스트가 쓰는 설정이라 컨텍스트도 새로 뜨지 않는다.
class PasswordProtectionTest extends ApiTestSupport {

    /** 시각을 못박는다. 설정을 나누지 않으므로 컨텍스트가 갈리지 않는다. */
    @Override
    protected Instant fixedNow() {
        return TestClocks.FIXED;
    }

    private static final String TARGET = "https://img.orino.dev/note-images/2026/aug.jpg";
    private static final String PASSWORD = "hunter2";

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        // 시도 횟수는 Redis에 남는다 — DB만 비우면 앞 테스트가 쓴 창이 그대로 이어진다.
        Set<String> attemptKeys = redisTemplate.keys("shortlink:unlock:*");
        if (!attemptKeys.isEmpty()) {
            redisTemplate.delete(attemptKeys);
        }
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        issue("jeju", PASSWORD);
        issue("busan", null);
    }

    @Nested
    @DisplayName("확인 화면")
    class Confirm {

        @Test
        @DisplayName("비밀번호 링크에 GET하면 302가 아니라 200 확인 화면이다")
        void asksForPassword() throws Exception {
            mockMvc.perform(get("/r/jeju"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(header().doesNotExist(HttpHeaders.LOCATION));

            assertThat(body("/r/jeju")).contains("비밀번호를 입력해 주세요");
        }

        @Test
        @DisplayName("확인 화면에 목적지도 슬러그도 담기지 않는다")
        void leaksNothing() throws Exception {
            String html = body("/r/jeju");

            assertThat(html).doesNotContain(TARGET);
            assertThat(html).doesNotContain("jeju");
            // form은 지금 주소로 그대로 POST한다 — 내부 경로(/r/, /unlock)가 HTML에 없다.
            assertThat(html).doesNotContain("/unlock").doesNotContain("/r/");
        }

        @Test
        @DisplayName("없는 슬러그는 여전히 404다 — 예외는 켠 링크에만 적용된다")
        void unknownSlugStillFails() throws Exception {
            mockMvc.perform(get("/r/nnnnn")).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("통과와 실패")
    class Unlock {

        @Test
        @DisplayName("맞는 비밀번호면 302 + no-store로 목적지에 보낸다")
        void redirectsOnCorrectPassword() throws Exception {
            unlock("jeju", PASSWORD)
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION, TARGET))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(header().string("Referrer-Policy", "no-referrer"));
        }

        @Test
        @DisplayName("통과해도 쿠키를 만들지 않는다 — 다음 방문에 다시 물어본다")
        void createsNoCookie() throws Exception {
            MockHttpServletResponse response = unlock("jeju", PASSWORD)
                    .andReturn().getResponse();

            assertThat(response.getCookies()).isEmpty();
            assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();

            // 곧바로 다시 방문해도 확인 화면이다.
            mockMvc.perform(get("/r/jeju")).andExpect(status().isOk());
            assertThat(body("/r/jeju")).contains("비밀번호를 입력해 주세요");
        }

        @Test
        @DisplayName("틀리면 같은 화면에 한 줄만 바뀐다 — 이유를 나누지 않는다")
        void showsSameScreenOnWrongPassword() throws Exception {
            String wrong = unlock("jeju", "nope").andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(wrong).contains("비밀번호가 맞지 않아요");
            // 비어 있는 입력도 "틀렸다"와 같은 취급이다.
            assertThat(unlock("jeju", "").andReturn().getResponse().getContentAsString())
                    .contains("비밀번호가 맞지 않아요");
        }

        @Test
        @DisplayName("비밀번호 없는 슬러그에 unlock POST가 오면 GET과 같은 판정이다")
        void treatsPasswordlessSlugLikeGet() throws Exception {
            // "이 링크에는 비밀번호가 없습니다"라고 알려주지 않는다 — 그 자체가 정보다.
            unlock("busan", "whatever")
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION, TARGET));
            unlock("nnnnn", "whatever").andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("재작성 없이 /r/{slug}로 POST가 와도 같은 판정이다 — form은 지금 주소로 보낸다")
        void acceptsPostOnPublicPathToo() throws Exception {
            mockMvc.perform(post("/r/jeju")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("password", PASSWORD))
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION, TARGET));
        }

        @Test
        @DisplayName("맞게 여러 번 열어도 잠기지 않는다 — 세는 것은 실패뿐이다")
        void doesNotCountSuccesses() throws Exception {
            // 세션을 만들지 않으므로 아는 사람도 열 때마다 입력한다. 성공까지 세면 스스로 잠긴다.
            for (int attempt = 1; attempt <= 12; attempt++) {
                unlock("jeju", PASSWORD).andExpect(status().isFound());
            }
        }

        @Test
        @DisplayName("11회째 시도는 429다 — 문구만 바뀌고 화면은 같다")
        void limitsAttemptsPerMinute() throws Exception {
            for (int attempt = 1; attempt <= 10; attempt++) {
                unlock("jeju", "nope").andExpect(status().isOk());
            }

            String blocked = unlock("jeju", "nope")
                    .andExpect(status().isTooManyRequests())
                    .andReturn().getResponse().getContentAsString();

            assertThat(blocked).contains("잠시 후 다시 시도해 주세요");
            // 맞는 비밀번호를 넣어도 창이 찼으면 열리지 않는다.
            unlock("jeju", PASSWORD).andExpect(status().isTooManyRequests());
        }

        @Test
        @DisplayName("시도 제한은 슬러그별이다 — 다른 링크는 영향받지 않는다")
        void limitsPerSlug() throws Exception {
            issue("seju", PASSWORD);
            for (int attempt = 1; attempt <= 11; attempt++) {
                unlock("jeju", "nope");
            }

            unlock("seju", PASSWORD).andExpect(status().isFound());
        }
    }

    @Nested
    @DisplayName("설정과 해제")
    class Manage {

        @Test
        @DisplayName("PATCH로 걸고 null로 해제한다 — 해제하면 바로 302다")
        void setsAndClearsPassword() throws Exception {
            mockMvc.perform(patch("/api/shortlinks/busan")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"password": "%s"}
                                    """.formatted(PASSWORD)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/r/busan")).andExpect(status().isOk());

            mockMvc.perform(patch("/api/shortlinks/busan")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"password": null}
                                    """))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/r/busan")).andExpect(status().isFound());
        }

        @Test
        @DisplayName("관리 API 응답에는 비밀번호가 실리지 않는다 — 여부만 안다")
        void exposesOnlyWhetherProtected() throws Exception {
            String detail = mockMvc.perform(get("/api/shortlinks/jeju")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andReturn().getResponse().getContentAsString();

            assertThat(detail).contains("\"hasPassword\":true");
            assertThat(detail).doesNotContain(PASSWORD);
            assertThat(detail).doesNotContain("passwordHash").doesNotContain("$2a$");
        }
    }

    private String body(String path) throws Exception {
        return mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString();
    }

    private ResultActions unlock(String slug, String password) throws Exception {
        return mockMvc.perform(post("/r/" + slug + "/unlock")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("password", password));
    }

    private void issue(String slug, String password) throws Exception {
        String body = password == null
                ? """
                {"targetUrl": "%s", "slug": "%s"}
                """.formatted(TARGET, slug)
                : """
                {"targetUrl": "%s", "slug": "%s", "password": "%s"}
                """.formatted(TARGET, slug, password);
        mockMvc.perform(post("/api/shortlinks")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
