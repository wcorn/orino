package ds.project.orino.planner.lifelog.flow.controller;

import com.jayway.jsonpath.JsonPath;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 흐름 CRUD·N:M 담기/빼기/정렬 통합 테스트. 흐름 조작은 기록을 지우지 않으므로(MinIO 미접촉)
 * 사진 포함 기록으로 커버 fallback까지 검증한다.
 */
class FlowControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
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

    @Test
    @DisplayName("POST /flows - ACTIVE 상태로 흐름을 만든다")
    void createFlow() throws Exception {
        mockMvc.perform(post("/api/lifelog/flows")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "제주 여행 2박3일", "description": "2026 여름"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("제주 여행 2박3일"))
                .andExpect(jsonPath("$.data.momentCount").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /flows?status= - 상태로 필터한다")
    void listByStatus() throws Exception {
        long active = createFlow("진행중");
        long archived = createFlow("보관대상");
        updateFlow(archived, "보관대상", "ARCHIVED");

        mockMvc.perform(get("/api/lifelog/flows").param("status", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value((int) active));
    }

    @Test
    @DisplayName("담기 후 상세는 시간순, 기간이 유도된다")
    void addAndDetailChronological() throws Exception {
        long flow = createFlow("제주 여행");
        long m2 = createMoment("""
                {"body": "낮", "occurredAt": "2026-07-20T05:00:00Z"}""");
        long m1 = createMoment("""
                {"body": "아침", "occurredAt": "2026-07-20T00:00:00Z"}""");
        long m3 = createMoment("""
                {"body": "저녁", "occurredAt": "2026-07-20T11:00:00Z"}""");

        // 단건 + 다건 혼합으로 담기.
        addMoments(flow, "{\"momentId\": %d}".formatted(m2));
        addMoments(flow, "{\"momentIds\": [%d, %d]}".formatted(m1, m3));

        mockMvc.perform(get("/api/lifelog/flows/{id}", flow)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.moments", hasSize(3)))
                .andExpect(jsonPath("$.data.moments[0].body").value("아침"))
                .andExpect(jsonPath("$.data.moments[1].body").value("낮"))
                .andExpect(jsonPath("$.data.moments[2].body").value("저녁"))
                // 발생시각은 요청 시간대(Asia/Seoul, +09:00)로 렌더된다: 00:00Z→09:00, 11:00Z→20:00.
                .andExpect(jsonPath("$.data.startedAt").value(containsString("2026-07-20T09:00:00")))
                .andExpect(jsonPath("$.data.endedAt").value(containsString("2026-07-20T20:00:00")));
    }

    @Test
    @DisplayName("담기는 멱등하고, 소유가 아닌 기록은 무시된다")
    void addIsIdempotentAndScoped() throws Exception {
        long flow = createFlow("F");
        long mine = createMoment("""
                {"body": "내것"}""");
        long theirs = createMomentAs(otherAuthHeader, """
                {"body": "남의것"}""");

        addMoments(flow, "{\"momentIds\": [%d, %d, %d]}".formatted(mine, mine, theirs));

        mockMvc.perform(get("/api/lifelog/flows/{id}", flow)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.moments", hasSize(1)))
                .andExpect(jsonPath("$.data.moments[0].body").value("내것"));
    }

    @Test
    @DisplayName("커버 미지정이면 시간순 첫 기록의 첫 사진을 커버로")
    void coverFallbackToFirstPhoto() throws Exception {
        long flow = createFlow("여행");
        long m = createMoment("""
                {"body": "사진기록", "photos": [
                  {"objectKey": "lifelog/moments/1/p.jpg", "thumbKey": "lifelog/thumbs/1/p.jpg"}
                ]}""");
        addMoments(flow, "{\"momentId\": %d}".formatted(m));

        // 상세
        mockMvc.perform(get("/api/lifelog/flows/{id}", flow)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.coverUrl", containsString("lifelog/thumbs/")));
        // 목록
        mockMvc.perform(get("/api/lifelog/flows")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data[0].coverUrl", containsString("lifelog/thumbs/")))
                .andExpect(jsonPath("$.data[0].momentCount").value(1));
    }

    @Test
    @DisplayName("PUT order - 흐름 내 순서를 재기록한다")
    void reorder() throws Exception {
        long flow = createFlow("F");
        long m1 = createMoment("""
                {"body": "1", "occurredAt": "2026-07-20T00:00:00Z"}""");
        long m2 = createMoment("""
                {"body": "2", "occurredAt": "2026-07-20T01:00:00Z"}""");
        long m3 = createMoment("""
                {"body": "3", "occurredAt": "2026-07-20T02:00:00Z"}""");
        addMoments(flow, "{\"momentIds\": [%d, %d, %d]}".formatted(m1, m2, m3));

        mockMvc.perform(put("/api/lifelog/flows/{id}/moments/order", flow)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"momentIds\": [%d, %d, %d]}".formatted(m3, m1, m2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.moments[0].body").value("3"))
                .andExpect(jsonPath("$.data.moments[1].body").value("1"))
                .andExpect(jsonPath("$.data.moments[2].body").value("2"));
    }

    @Test
    @DisplayName("빼기는 소속만 제거하고 기록은 남긴다")
    void removeKeepsMoment() throws Exception {
        long flow = createFlow("F");
        long m1 = createMoment("""
                {"body": "a"}""");
        long m2 = createMoment("""
                {"body": "b"}""");
        addMoments(flow, "{\"momentIds\": [%d, %d]}".formatted(m1, m2));

        mockMvc.perform(delete("/api/lifelog/flows/{fid}/moments/{mid}", flow, m1)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/lifelog/flows/{id}", flow)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.moments", hasSize(1)))
                .andExpect(jsonPath("$.data.moments[0].body").value("b"));
        // 뺀 기록 자체는 보존.
        mockMvc.perform(get("/api/lifelog/moments/{id}", m1)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("흐름 삭제는 소속만 지우고 담겼던 기록은 보존한다")
    void deleteFlowPreservesMoments() throws Exception {
        long flow = createFlow("버릴흐름");
        long m = createMoment("""
                {"body": "살아남을것"}""");
        addMoments(flow, "{\"momentId\": %d}".formatted(m));

        mockMvc.perform(delete("/api/lifelog/flows/{id}", flow)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/lifelog/flows/{id}", flow)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/lifelog/moments/{id}", m)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("다른 멤버의 흐름은 404")
    void flowScoped() throws Exception {
        long flow = createFlow("내흐름");

        mockMvc.perform(get("/api/lifelog/flows/{id}", flow)
                        .header(HttpHeaders.AUTHORIZATION, otherAuthHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LIFELOG-ERR-005"));
    }

    @Test
    @DisplayName("GET /flows - 인증 없으면 401")
    void requiresAuth() throws Exception {
        mockMvc.perform(get("/api/lifelog/flows"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------- helpers ----------------

    private long createFlow(String title) throws Exception {
        String body = mockMvc.perform(post("/api/lifelog/flows")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"%s\"}".formatted(title)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private void updateFlow(long id, String title, String status) throws Exception {
        mockMvc.perform(put("/api/lifelog/flows/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"%s\", \"status\": \"%s\"}".formatted(title, status)))
                .andExpect(status().isOk());
    }

    private void addMoments(long flowId, String json) throws Exception {
        mockMvc.perform(post("/api/lifelog/flows/{id}/moments", flowId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    private long createMoment(String json) throws Exception {
        return createMomentAs(authHeader, json);
    }

    private long createMomentAs(String auth, String json) throws Exception {
        String body = mockMvc.perform(post("/api/lifelog/moments")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }
}
