package ds.project.orino.planner.shortlink;

import ds.project.orino.domain.member.repository.MemberRepository;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공개 리다이렉트 {@code /r/{slug}}(#1237).
 *
 * <p>이 테스트의 절반은 <b>응답이 아무것도 알려주지 않는지</b>를 본다. 없음 · 꺼짐 · 만료 ·
 * 삭제가 상태 · 본문 · 헤더까지 같아야 하고(명세 §7), 하나라도 갈리면 방문자는 그 차이로
 * "이 슬러그는 존재한다"를 알아낸다.
 *
 * <p>나머지 절반은 302 계약이다. 301이거나 캐시가 걸리면 목적지 교체가 먹지 않고,
 * 그 순간 이 모듈의 존재 이유가 사라진다.
 */
class RedirectControllerTest extends ApiTestSupport {

    private static final String TARGET = "https://img.orino.dev/note-images/2026/aug.jpg";
    private static final String SIGNED_TARGET =
            "https://img.orino.dev/photos/aug.zip?X-Amz-Signature=deadbeef";

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    @Nested
    @DisplayName("302 계약")
    class Redirect {

        @Test
        @DisplayName("활성 링크는 302 + no-store + no-referrer로 목적지를 가리킨다")
        void redirectsWithNoStore() throws Exception {
            issue("jeju", TARGET);

            visit("/r/jeju")
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION, TARGET))
                    // 301이거나 캐시가 걸리면 목적지 교체(명세 §5.1)가 먹지 않는다.
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(header().string("Referrer-Policy", "no-referrer"));
        }

        @Test
        @DisplayName("대문자로 방문해도 같은 링크로 간다 — 소문자로 정규화해 조회한다")
        void normalizesUppercaseSlug() throws Exception {
            issue("jeju", TARGET);

            visit("/r/JeJu")
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION, TARGET));
        }

        @Test
        @DisplayName("목적지를 갈아끼우면 같은 주소가 새 목적지로 간다 — 이 모듈의 존재 이유")
        void followsReplacedTarget() throws Exception {
            issue("jeju", TARGET);
            String replaced = "https://img.orino.dev/note-images/2026/sep.jpg";
            mockMvc.perform(patch("/api/shortlinks/jeju")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"targetUrl": "%s"}
                                    """.formatted(replaced)))
                    .andExpect(status().isOk());

            visit("/r/jeju").andExpect(header().string(HttpHeaders.LOCATION, replaced));
        }

        @Test
        @DisplayName("방문 쿼리가 없으면 목적지에 ?를 붙이지 않는다")
        void doesNotAddQuestionMark() throws Exception {
            issue("jeju", TARGET);

            visit("/r/jeju").andExpect(header().string(HttpHeaders.LOCATION, TARGET));
        }

        @Test
        @DisplayName("방문 쿼리는 목적지 뒤에 이어붙인다")
        void appendsVisitQuery() throws Exception {
            issue("jeju", TARGET);

            visit("/r/jeju?utm=kakao")
                    .andExpect(header().string(HttpHeaders.LOCATION, TARGET + "?utm=kakao"));
        }

        @Test
        @DisplayName("서명된 목적지에는 방문 쿼리를 전달하지 않는다 — 서명이 깨진다")
        void dropsVisitQueryForSignedTarget() throws Exception {
            issue("sign", SIGNED_TARGET);

            visit("/r/sign?utm=kakao")
                    .andExpect(header().string(HttpHeaders.LOCATION, SIGNED_TARGET));
        }
    }

    @Nested
    @DisplayName("404 하나")
    class SingleFailure {

        @Test
        @DisplayName("없음 · 꺼짐 · 만료 · 삭제의 응답이 상태 · 본문 · 헤더까지 같다")
        void fourFailuresAreIndistinguishable() throws Exception {
            issue("busan", TARGET);
            mockMvc.perform(post("/api/shortlinks/busan/toggle")
                    .header(HttpHeaders.AUTHORIZATION, authHeader));

            mockMvc.perform(post("/api/shortlinks")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"targetUrl": "%s", "slug": "seju", "expiresAt": "2020-01-01T00:00:00Z"}
                            """.formatted(TARGET)));

            issue("jeju", TARGET);
            mockMvc.perform(delete("/api/shortlinks/jeju")
                    .header(HttpHeaders.AUTHORIZATION, authHeader));

            Map<String, String> fingerprints = new LinkedHashMap<>();
            fingerprints.put("없음", fingerprint("/r/nnnnn"));
            fingerprints.put("꺼짐", fingerprint("/r/busan"));
            fingerprints.put("만료", fingerprint("/r/seju"));
            fingerprints.put("삭제", fingerprint("/r/jeju"));

            assertThat(fingerprints.values()).containsOnly(fingerprints.get("없음"));
        }

        @Test
        @DisplayName("410이 아니라 404다 — 410은 「예전엔 있었다」를 알려준다")
        void usesNotFoundNeverGone() throws Exception {
            issue("jeju", TARGET);
            mockMvc.perform(delete("/api/shortlinks/jeju")
                    .header(HttpHeaders.AUTHORIZATION, authHeader));

            visit("/r/jeju").andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패 화면에는 목적지도 메모도 없다 — 브랜드 마크와 한 줄뿐")
        void failurePageLeaksNothing() throws Exception {
            issue("jeju", "https://secret.example.com/private?token=abc");
            mockMvc.perform(delete("/api/shortlinks/jeju")
                    .header(HttpHeaders.AUTHORIZATION, authHeader));

            String body = visit("/r/jeju").andReturn().getResponse().getContentAsString();

            assertThat(body).contains("이 링크는 사용할 수 없습니다");
            assertThat(body).doesNotContain("secret.example.com");
            assertThat(body).doesNotContain("jeju");
            // 외부 요청 0 — 폰트·스크립트·이미지를 부르지 않는다(아키텍처 §2.3).
            assertThat(body).doesNotContain("http://");
            assertThat(body).doesNotContain("https://");
        }

        @Test
        @DisplayName("비밀번호가 걸린 링크는 확인 화면(#1244) 전까지 열어 주지 않는다")
        void failsClosedForPasswordProtectedLink() throws Exception {
            mockMvc.perform(post("/api/shortlinks")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"targetUrl": "%s", "slug": "pwd", "password": "hunter2"}
                            """.formatted(TARGET)));

            visit("/r/pwd").andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("인증 경계")
    class AuthBoundary {

        @Test
        @DisplayName("/r/**만 인증 없이 열린다 — /api/shortlinks/**는 여전히 401")
        void onlyRedirectIsPublic() throws Exception {
            issue("jeju", TARGET);

            visit("/r/jeju").andExpect(status().isFound());
            mockMvc.perform(get("/api/shortlinks")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/shortlinks/jeju")).andExpect(status().isUnauthorized());
        }
    }

    /** 상태 · 본문 · 헤더를 한 문자열로 묶는다. 하나라도 갈리면 비교에서 드러난다. */
    private String fingerprint(String path) throws Exception {
        MockHttpServletResponse response = visit(path).andReturn().getResponse();
        return response.getStatus()
                + "|" + response.getContentType()
                + "|" + response.getHeader(HttpHeaders.CACHE_CONTROL)
                + "|" + response.getHeader("Referrer-Policy")
                + "|" + response.getHeader(HttpHeaders.LOCATION)
                + "|" + response.getContentAsString();
    }

    private ResultActions visit(String path) throws Exception {
        return mockMvc.perform(get(path));
    }

    private void issue(String slug, String targetUrl) throws Exception {
        mockMvc.perform(post("/api/shortlinks")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetUrl": "%s", "slug": "%s"}
                                """.formatted(targetUrl, slug)))
                .andExpect(status().isOk());
    }
}
