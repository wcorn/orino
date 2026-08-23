package ds.project.orino.planner.shortlink;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.shortlink.entity.Shortlink;
import ds.project.orino.domain.planner.shortlink.entity.ShortlinkStatus;
import ds.project.orino.domain.planner.shortlink.repository.ShortlinkRepository;
import ds.project.orino.planner.shortlink.service.SlugPolicy;
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
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 링크 관리 API(#1236).
 *
 * <p>이 테스트가 고정하는 것은 <b>이 모듈이 틀리면 안 되는 것들</b>이다 — 슬러그 영구 점유,
 * 슬러그 불변, 목적지가 실제로 바뀔 때만 남는 이력, 만료의 파생. 넷 중 하나라도 무너지면
 * "한 번 뿌린 주소를 죽지 않게 한다"는 이 모듈의 존재 이유가 깨진다.
 */
class ShortlinkControllerTest extends ApiTestSupport {

    private static final String TARGET =
            "https://img.orino.dev/note-images/2026/aug.jpg";

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ShortlinkRepository shortlinkRepository;
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
        otherAuthHeader = "Bearer "
                + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");
    }

    @Nested
    @DisplayName("발급")
    class Create {

        @Test
        @DisplayName("URL 하나만 보내면 5자 슬러그와 짧은 주소가 돌아온다")
        void issuesAutoSlug() throws Exception {
            String slug = JsonPath.read(create("""
                    {"targetUrl": "%s"}
                    """.formatted(TARGET))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.targetUrl").value(TARGET))
                    .andExpect(jsonPath("$.data.custom").value(false))
                    .andExpect(jsonPath("$.data.state").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.visitCount").value(0))
                    .andReturn().getResponse().getContentAsString(), "$.data.slug");

            assertThat(slug).hasSize(SlugPolicy.AUTO_LENGTH);
            // 0 · o · 1 · l이 빠진 32자 — 입으로 부르거나 손으로 옮겨 적을 때 갈리는 글자들.
            for (char c : slug.toCharArray()) {
                assertThat(SlugPolicy.ALPHABET.indexOf(c)).isNotNegative();
            }
        }

        @Test
        @DisplayName("짧은 주소와 QR 페이로드를 서버가 조립해 내려준다")
        void assemblesShortUrlOnServer() throws Exception {
            String body = create("""
                    {"targetUrl": "%s", "slug": "jeju"}
                    """.formatted(TARGET)).andReturn().getResponse().getContentAsString();

            assertThat(JsonPath.<String>read(body, "$.data.shortUrl"))
                    .isEqualTo("https://s.orino.dev/jeju");
            assertThat(JsonPath.<String>read(body, "$.data.qrPayload"))
                    .isEqualTo("https://s.orino.dev/jeju");
        }

        @Test
        @DisplayName("발급과 동시에 「최초 발급」 이력 한 줄이 남는다")
        void writesInitialHistory() throws Exception {
            create("""
                    {"targetUrl": "%s", "slug": "jeju"}
                    """.formatted(TARGET));

            detail("jeju")
                    .andExpect(jsonPath("$.data.targetHistory", hasSize(1)))
                    .andExpect(jsonPath("$.data.targetHistory[0].targetUrl").value(TARGET))
                    .andExpect(jsonPath("$.data.targetHistory[0].reason").value("최초 발급"));
        }

        @Test
        @DisplayName("커스텀 슬러그는 대문자로 와도 소문자로 저장된다")
        void normalizesCustomSlug() throws Exception {
            create("""
                    {"targetUrl": "%s", "slug": "JeJu"}
                    """.formatted(TARGET))
                    .andExpect(jsonPath("$.data.slug").value("jeju"))
                    .andExpect(jsonPath("$.data.custom").value(true));
        }

        @Test
        @DisplayName("javascript: 같은 스킴은 거부한다 — SL-ERR-001")
        void rejectsUnsupportedScheme() throws Exception {
            create("""
                    {"targetUrl": "javascript:alert(1)"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SL-ERR-001"));
        }

        @Test
        @DisplayName("목적지가 단축 주소 자신이면 거부한다 — SL-ERR-002")
        void rejectsSelfReference() throws Exception {
            create("""
                    {"targetUrl": "https://s.orino.dev/ab3k9"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SL-ERR-002"));
        }

        @Test
        @DisplayName("문자셋 밖 글자가 있으면 거부한다 — SL-ERR-004")
        void rejectsSlugOutsideAlphabet() throws Exception {
            // o · l은 문자셋에서 뺀 글자다. 게이트웨이가 걸러 내므로 발급돼도 열리지 않는다.
            create("""
                    {"targetUrl": "%s", "slug": "hello"}
                    """.formatted(TARGET))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SL-ERR-004"));
        }

        @Test
        @DisplayName("살아 있는 슬러그로 재발급하면 거부한다 — SL-ERR-003")
        void rejectsDuplicateSlug() throws Exception {
            create("""
                    {"targetUrl": "%s", "slug": "jeju"}
                    """.formatted(TARGET));

            create("""
                    {"targetUrl": "%s", "slug": "jeju"}
                    """.formatted(TARGET))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SL-ERR-003"));
        }

        @Test
        @DisplayName("삭제한 링크의 슬러그도 영구 점유다 — 재발급이 SL-ERR-003으로 막힌다")
        void keepsSlugTakenAfterDelete() throws Exception {
            create("""
                    {"targetUrl": "%s", "slug": "jeju"}
                    """.formatted(TARGET));
            mockMvc.perform(delete("/api/shortlinks/jeju")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            create("""
                    {"targetUrl": "%s", "slug": "jeju"}
                    """.formatted(TARGET))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SL-ERR-003"));

            // 왜 막혔는지도 구분해 알려주지 않는다 — 삭제된 것인지 살아 있는 것인지.
            mockMvc.perform(get("/api/shortlinks/slug-available")
                            .param("slug", "jeju")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.available").value(false));
        }
    }

    @Nested
    @DisplayName("목록")
    class ListLinks {

        @Test
        @DisplayName("즐겨찾기와 최근 발급이 나뉘고, 같은 카드가 두 번 나오지 않는다")
        void splitsFavorites() throws Exception {
            create("""
                    {"targetUrl": "%s", "slug": "jeju"}
                    """.formatted(TARGET));
            create("""
                    {"targetUrl": "%s", "slug": "busan"}
                    """.formatted(TARGET));
            mockMvc.perform(post("/api/shortlinks/jeju/favorite")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.favorite").value(true));

            list("")
                    .andExpect(jsonPath("$.data.counts.all").value(2))
                    .andExpect(jsonPath("$.data.favorites", hasSize(1)))
                    .andExpect(jsonPath("$.data.favorites[0].slug").value("jeju"))
                    .andExpect(jsonPath("$.data.recent", hasSize(1)))
                    .andExpect(jsonPath("$.data.recent[0].slug").value("busan"));
        }

        @Test
        @DisplayName("검색은 슬러그·목적지·메모를 함께 본다")
        void searchesAcrossFields() throws Exception {
            create("""
                    {"targetUrl": "%s", "slug": "jeju", "memo": "부모님께 보낸 8월 흐름"}
                    """.formatted(TARGET));
            create("""
                    {"targetUrl": "https://example.com/other", "slug": "busan"}
                    """);

            list("?query=부모님")
                    .andExpect(jsonPath("$.data.recent", hasSize(1)))
                    .andExpect(jsonPath("$.data.recent[0].slug").value("jeju"));
            list("?query=example.com")
                    .andExpect(jsonPath("$.data.recent", hasSize(1)))
                    .andExpect(jsonPath("$.data.recent[0].slug").value("busan"));
        }

        @Test
        @DisplayName("상태 칩의 INACTIVE는 꺼짐과 만료를 함께 담는다")
        void inactiveHoldsDisabledAndExpired() throws Exception {
            create("""
                    {"targetUrl": "%s", "slug": "jeju"}
                    """.formatted(TARGET));
            create("""
                    {"targetUrl": "%s", "slug": "busan"}
                    """.formatted(TARGET));
            create("""
                    {"targetUrl": "%s", "slug": "seju", "expiresAt": "2020-01-01T00:00:00Z"}
                    """.formatted(TARGET));
            mockMvc.perform(post("/api/shortlinks/busan/toggle")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.state").value("DISABLED"));

            list("")
                    .andExpect(jsonPath("$.data.counts.all").value(3))
                    .andExpect(jsonPath("$.data.counts.active").value(1))
                    .andExpect(jsonPath("$.data.counts.inactive").value(2));
            list("?status=INACTIVE")
                    .andExpect(jsonPath("$.data.recent", hasSize(2)))
                    // 상태로 걸러도 칩 숫자는 그대로다 — 걸러진 뒤에 세면 다른 칩을 알 수 없다.
                    .andExpect(jsonPath("$.data.counts.all").value(3));
            list("?status=ACTIVE")
                    .andExpect(jsonPath("$.data.recent", hasSize(1)))
                    .andExpect(jsonPath("$.data.recent[0].slug").value("jeju"));
        }

        @Test
        @DisplayName("태그로 거르고, 사이드바 태그 개수는 살아 있는 링크만 센다")
        void filtersByTag() throws Exception {
            create("""
                    {"targetUrl": "%s", "slug": "jeju", "tags": ["가족", "가족", " "]}
                    """.formatted(TARGET));
            create("""
                    {"targetUrl": "%s", "slug": "busan", "tags": ["여행"]}
                    """.formatted(TARGET));

            list("?tag=가족")
                    .andExpect(jsonPath("$.data.recent", hasSize(1)))
                    // 같은 태그를 두 번 보내도 한 번만 담기고, 빈 태그는 버린다.
                    .andExpect(jsonPath("$.data.recent[0].tags", hasSize(1)))
                    .andExpect(jsonPath("$.data.recent[0].tags[0]").value("가족"));

            mockMvc.perform(delete("/api/shortlinks/busan")
                    .header(HttpHeaders.AUTHORIZATION, authHeader));

            mockMvc.perform(get("/api/shortlinks/tags")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].name").value("가족"))
                    .andExpect(jsonPath("$.data[0].count").value(1));
        }
    }

    @Nested
    @DisplayName("편집 · 목적지 교체")
    class Update {

        @Test
        @DisplayName("목적지를 갈아끼우면 이력이 시간 역순으로 쌓인다 — 마지막 줄이 최초 발급")
        void appendsHistoryOnTargetChange() throws Exception {
            create("""
                    {"targetUrl": "%s", "slug": "jeju"}
                    """.formatted(TARGET));

            patchLink("jeju", """
                    {"targetUrl": "https://img.orino.dev/note-images/2026/sep.jpg",
                     "targetChangeReason": "서명 만료로 재발급"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.targetHistory", hasSize(2)))
                    .andExpect(jsonPath("$.data.targetHistory[0].reason").value("서명 만료로 재발급"))
                    .andExpect(jsonPath("$.data.targetHistory[1].reason").value("최초 발급"))
                    // 주소는 그대로다. 이미 뿌린 링크가 전부 살아난다.
                    .andExpect(jsonPath("$.data.slug").value("jeju"));
        }

        @Test
        @DisplayName("같은 목적지를 다시 보내면 이력이 늘지 않는다")
        void skipsHistoryWhenTargetUnchanged() throws Exception {
            create("""
                    {"targetUrl": "%s", "slug": "jeju"}
                    """.formatted(TARGET));

            patchLink("jeju", """
                    {"targetUrl": "%s", "memo": "메모만 고친다"}
                    """.formatted(TARGET))
                    .andExpect(jsonPath("$.data.memo").value("메모만 고친다"))
                    .andExpect(jsonPath("$.data.targetHistory", hasSize(1)));
        }

        @Test
        @DisplayName("slug를 실어 보내도 무시한다 — 슬러그는 바꿀 수 없다")
        void ignoresSlugInPatch() throws Exception {
            create("""
                    {"targetUrl": "%s", "slug": "jeju"}
                    """.formatted(TARGET));

            patchLink("jeju", """
                    {"slug": "busan", "memo": "바꿔치기 시도"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.slug").value("jeju"));

            detail("busan").andExpect(status().isNotFound());
            assertThat(shortlinkRepository.existsBySlug("busan")).isFalse();
        }

        @Test
        @DisplayName("expiresAt은 안 보내면 그대로, null을 보내면 해제된다")
        void clearsExpiryOnlyWithExplicitNull() throws Exception {
            create("""
                    {"targetUrl": "%s", "slug": "jeju", "expiresAt": "2020-01-01T00:00:00Z"}
                    """.formatted(TARGET))
                    .andExpect(jsonPath("$.data.state").value("EXPIRED"));

            patchLink("jeju", """
                    {"memo": "만료는 건드리지 않는다"}
                    """)
                    .andExpect(jsonPath("$.data.state").value("EXPIRED"));

            patchLink("jeju", """
                    {"expiresAt": null}
                    """)
                    .andExpect(jsonPath("$.data.state").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.expiresAt").isEmpty());
        }

        @Test
        @DisplayName("만료는 저장값을 바꾸지 않는다 — status는 ACTIVE인 채 화면에만 EXPIRED로 보인다")
        void derivesExpiredWithoutTouchingStoredStatus() throws Exception {
            create("""
                    {"targetUrl": "%s", "slug": "jeju", "expiresAt": "2020-01-01T00:00:00Z"}
                    """.formatted(TARGET));

            detail("jeju").andExpect(jsonPath("$.data.state").value("EXPIRED"));

            Shortlink stored = shortlinkRepository
                    .findBySlugAndMemberIdAndDeletedAtIsNull("jeju", memberId()).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(ShortlinkStatus.ACTIVE);
        }

        @Test
        @DisplayName("비밀번호는 켜고 끌 수 있고, 응답에는 여부만 나간다")
        void togglesPassword() throws Exception {
            create("""
                    {"targetUrl": "%s", "slug": "jeju", "password": "hunter2"}
                    """.formatted(TARGET))
                    .andExpect(jsonPath("$.data.hasPassword").value(true))
                    .andExpect(jsonPath("$.data.password").doesNotExist());

            patchLink("jeju", """
                    {"password": null}
                    """)
                    .andExpect(jsonPath("$.data.hasPassword").value(false));
        }

        @Test
        @DisplayName("편집도 목적지 검증을 그대로 통과해야 한다")
        void validatesTargetOnUpdate() throws Exception {
            create("""
                    {"targetUrl": "%s", "slug": "jeju"}
                    """.formatted(TARGET));

            patchLink("jeju", """
                    {"targetUrl": "https://s.orino.dev/jeju"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SL-ERR-002"));
        }
    }

    @Nested
    @DisplayName("상태 · 삭제 · 소유권")
    class StateAndOwnership {

        @Test
        @DisplayName("삭제하면 목록에서 사라지고 상세는 404다 — SL-ERR-006")
        void softDeleteHidesLink() throws Exception {
            create("""
                    {"targetUrl": "%s", "slug": "jeju"}
                    """.formatted(TARGET));

            mockMvc.perform(delete("/api/shortlinks/jeju")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            detail("jeju")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SL-ERR-006"));
            list("").andExpect(jsonPath("$.data.counts.all").value(0));
            // 행은 남아 있다 — 그게 슬러그 영구 점유를 집행한다.
            assertThat(shortlinkRepository.existsBySlug("jeju")).isTrue();
        }

        @Test
        @DisplayName("남의 링크는 404다 — 403이면 그 슬러그가 있다는 사실이 샌다")
        void hidesOtherMembersLink() throws Exception {
            create("""
                    {"targetUrl": "%s", "slug": "jeju"}
                    """.formatted(TARGET));

            mockMvc.perform(get("/api/shortlinks/jeju")
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SL-ERR-006"));
        }

        @Test
        @DisplayName("토큰이 없으면 401 — 관리 API는 전부 JWT 뒤에 있다")
        void requiresAuth() throws Exception {
            mockMvc.perform(get("/api/shortlinks"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("/select 카드 메타는 살아 있는 링크 수를 센다")
        void summarizesForWorkspaceCard() throws Exception {
            create("""
                    {"targetUrl": "%s", "slug": "jeju"}
                    """.formatted(TARGET));
            create("""
                    {"targetUrl": "%s", "slug": "busan"}
                    """.formatted(TARGET));
            mockMvc.perform(delete("/api/shortlinks/busan")
                    .header(HttpHeaders.AUTHORIZATION, authHeader));

            mockMvc.perform(get("/api/shortlinks/summary")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.total").value(1))
                    // 통계(#1240) 전까지 0. 필드는 미리 내려 FE 계약을 고정한다.
                    .andExpect(jsonPath("$.data.visitsThisWeek").value(0));
        }
    }

    private Long memberId() {
        return memberRepository.findByLoginId(MemberFixture.DEFAULT_LOGIN_ID).orElseThrow().getId();
    }

    private ResultActions create(String body) throws Exception {
        return mockMvc.perform(post("/api/shortlinks")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions patchLink(String slug, String body) throws Exception {
        return mockMvc.perform(patch("/api/shortlinks/" + slug)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions detail(String slug) throws Exception {
        return mockMvc.perform(get("/api/shortlinks/" + slug)
                .header(HttpHeaders.AUTHORIZATION, authHeader));
    }

    private ResultActions list(String queryString) throws Exception {
        return mockMvc.perform(get("/api/shortlinks" + queryString)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }
}
