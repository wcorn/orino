package ds.project.orino.planner.lifelog.moment.controller;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 기록 CRUD·피드·태그 자동완성 통합 테스트. 사진 삭제(MinIO)는 별도 유닛 테스트(#951)가 다루므로
 * 여기서는 삭제/수정 시 <b>제거되는 사진 key가 없도록</b> 시나리오를 짜 외부 호출 없이 검증한다.
 */
class MomentControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private Member otherMember;
    private String authHeader;
    private String otherAuthHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        otherMember = memberRepository.save(MemberFixture.create("other", "password"));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        otherAuthHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");
    }

    // ---------------- create ----------------

    @Test
    @DisplayName("POST /moments - 사진·태그·위치·기분을 담은 리치 카드를 만든다")
    void createRichMoment() throws Exception {
        postMoment(authHeader, """
                {
                  "occurredAt": "2026-07-20T05:30:00Z",
                  "body": "성산일출봉 정상",
                  "mood": "EXCITED",
                  "lat": 33.4580000, "lng": 126.9420000,
                  "placeName": "성산일출봉",
                  "tags": ["제주", "여행", "제주"],
                  "photos": [
                    {"objectKey": "lifelog/moments/1/a.jpg", "thumbKey": "lifelog/thumbs/1/a.jpg", "sortOrder": 0}
                  ]
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("성산일출봉 정상"))
                .andExpect(jsonPath("$.data.mood").value("EXCITED"))
                .andExpect(jsonPath("$.data.placeName").value("성산일출봉"))
                .andExpect(jsonPath("$.data.tags", hasSize(2)))
                .andExpect(jsonPath("$.data.photos", hasSize(1)))
                .andExpect(jsonPath("$.data.photos[0].url",
                        startsWith("https://img.orino.dev/note-images/lifelog/moments/")))
                .andExpect(jsonPath("$.data.photos[0].thumbUrl", containsString("lifelog/thumbs/")))
                .andExpect(jsonPath("$.data.flows", hasSize(0)));
    }

    @Test
    @DisplayName("POST /moments - 사진 없는 텍스트 기록도 만든다")
    void createTextOnly() throws Exception {
        postMoment(authHeader, """
                {"body": "오늘 커피 맛있었다"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("오늘 커피 맛있었다"))
                .andExpect(jsonPath("$.data.occurredAt").exists());
    }

    @Test
    @DisplayName("POST /moments - 본문·사진 모두 없으면 400")
    void rejectEmpty() throws Exception {
        postMoment(authHeader, """
                {"tags": ["빈것"]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LIFELOG-ERR-003"));
    }

    @Test
    @DisplayName("POST /moments - 위도만 있으면 400")
    void rejectCoordMismatch() throws Exception {
        postMoment(authHeader, """
                {"body": "x", "lat": 33.45}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LIFELOG-ERR-004"));
    }

    // ---------------- feed ----------------

    @Test
    @DisplayName("GET /moments - 역시간순 + 커서 페이지네이션")
    void feedOrderAndCursor() throws Exception {
        create("""
                {"body": "A", "occurredAt": "2026-07-20T01:00:00Z"}""");
        create("""
                {"body": "B", "occurredAt": "2026-07-20T02:00:00Z"}""");
        create("""
                {"body": "C", "occurredAt": "2026-07-20T03:00:00Z"}""");

        String body = mockMvc.perform(get("/api/lifelog/moments").param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].body").value("C"))
                .andExpect(jsonPath("$.data.items[1].body").value("B"))
                .andExpect(jsonPath("$.data.nextCursor").exists())
                .andReturn().getResponse().getContentAsString();

        String cursor = JsonPath.read(body, "$.data.nextCursor");
        mockMvc.perform(get("/api/lifelog/moments").param("size", "2").param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].body").value("A"))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("GET /moments?tag= - 태그로 필터한다")
    void feedTagFilter() throws Exception {
        create("""
                {"body": "제주기록", "tags": ["제주"]}""");
        create("""
                {"body": "그냥기록", "tags": ["일상"]}""");

        mockMvc.perform(get("/api/lifelog/moments").param("tag", "제주")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].body").value("제주기록"));
    }

    // ---------------- findOne / scope ----------------

    @Test
    @DisplayName("GET /moments/{id} - 조회, 다른 멤버는 404")
    void findOneScoped() throws Exception {
        long id = create("""
                {"body": "내 기록"}""");

        mockMvc.perform(get("/api/lifelog/moments/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("내 기록"));

        mockMvc.perform(get("/api/lifelog/moments/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, otherAuthHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LIFELOG-ERR-002"));
    }

    // ---------------- update ----------------

    @Test
    @DisplayName("PUT /moments/{id} - 본문·태그·사진을 치환한다(제거 사진 없음)")
    void updateReplaces() throws Exception {
        long id = create("""
                {"body": "원본", "tags": ["old"]}""");

        mockMvc.perform(put("/api/lifelog/moments/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "body": "수정본",
                                  "tags": ["new1", "new2"],
                                  "photos": [{"objectKey": "lifelog/moments/1/z.jpg", "sortOrder": 0}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("수정본"))
                .andExpect(jsonPath("$.data.tags", hasSize(2)))
                .andExpect(jsonPath("$.data.photos", hasSize(1)));
    }

    // ---------------- delete ----------------

    @Test
    @DisplayName("DELETE /moments/{id} - 삭제 후 404 (사진 없는 기록)")
    void deleteMoment() throws Exception {
        long id = create("""
                {"body": "지울것", "tags": ["t"]}""");

        mockMvc.perform(delete("/api/lifelog/moments/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/lifelog/moments/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /moments/{id} - 다른 멤버는 404, 기록은 남는다")
    void deleteScoped() throws Exception {
        long id = create("""
                {"body": "내것"}""");

        mockMvc.perform(delete("/api/lifelog/moments/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, otherAuthHeader))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/lifelog/moments/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }

    // ---------------- tags autocomplete ----------------

    @Test
    @DisplayName("GET /tags?q= - 멤버가 쓴 태그 접두어 자동완성(중복 제거)")
    void tagAutocomplete() throws Exception {
        create("""
                {"body": "a", "tags": ["제주", "여행"]}""");
        create("""
                {"body": "b", "tags": ["제주", "제주도"]}""");

        mockMvc.perform(get("/api/lifelog/tags").param("q", "제주")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0]").value("제주"))
                .andExpect(jsonPath("$.data[1]").value("제주도"));
    }

    // ---------------- auth ----------------

    @Test
    @DisplayName("GET /moments - 인증 없으면 401")
    void requiresAuth() throws Exception {
        mockMvc.perform(get("/api/lifelog/moments"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------- helpers ----------------

    private ResultActions postMoment(String auth, String json) throws Exception {
        return mockMvc.perform(post("/api/lifelog/moments")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private long create(String json) throws Exception {
        String body = postMoment(authHeader, json)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }
}
