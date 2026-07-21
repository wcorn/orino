package ds.project.orino.planner.dataset.controller;

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
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 열 푸터 요약 <b>함수</b> 설정/해제(#907 표면). 이 페이즈는 함수만 다루고 값(집계)은 계산하지
 * 않는다 — 응답 {@code summaries}엔 열 key만 담기고 값은 null이다(#908에서 채운다).
 */
class DatasetColumnSummaryTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private long datasetId;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);

        String body = mockMvc.perform(post("/api/datasets")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columns":[{"key":"c0","label":"단가"},{"key":"c1","label":"수량"}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        datasetId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private ResultActions setSummary(String key, String bodyJson) throws Exception {
        return mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/summary", datasetId, key)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyJson));
    }

    private ResultActions meta() throws Exception {
        return mockMvc.perform(get("/api/datasets/{id}", datasetId)
                .header(HttpHeaders.AUTHORIZATION, authHeader));
    }

    @Test
    @DisplayName("요약 함수를 설정하면 열에 함수가 담기고 summaries에 그 열 자리가 생긴다(값은 null)")
    void setSummarySetsFunctionAndSlot() throws Exception {
        setSummary("c0", "{\"summary\":\"SUM\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[0].summary").value("SUM"))
                // 값 자리는 열 key로 존재하되 값은 null(집계는 #908).
                .andExpect(jsonPath("$.data.summaries", hasKey("c0")))
                .andExpect(jsonPath("$.data.summaries.c1").doesNotExist());

        // 조회에도 그대로 실린다.
        meta().andExpect(jsonPath("$.data.columns[0].summary").value("SUM"))
                .andExpect(jsonPath("$.data.summaries", hasKey("c0")));
    }

    @Test
    @DisplayName("다시 설정하면 교체된다(열당 1개, 멱등)")
    void replaceIsIdempotent() throws Exception {
        setSummary("c0", "{\"summary\":\"SUM\"}").andExpect(status().isOk());
        setSummary("c0", "{\"summary\":\"AVERAGE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[0].summary").value("AVERAGE"));
    }

    @Test
    @DisplayName("null이면 요약이 해제된다")
    void clearRemovesSummary() throws Exception {
        setSummary("c0", "{\"summary\":\"SUM\"}").andExpect(status().isOk());
        setSummary("c0", "{\"summary\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[0].summary").doesNotExist())
                .andExpect(jsonPath("$.data.summaries", not(hasKey("c0"))));
    }

    @Test
    @DisplayName("허용되지 않은 함수는 400")
    void invalidFunctionRejected() throws Exception {
        setSummary("c0", "{\"summary\":\"MEDIAN\"}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("없는 열은 404")
    void missingColumnRejected() throws Exception {
        setSummary("c9", "{\"summary\":\"SUM\"}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("요약이 걸린 열을 지우면 요약도 함께 사라진다")
    void deletingColumnRemovesItsSummary() throws Exception {
        setSummary("c0", "{\"summary\":\"SUM\"}").andExpect(status().isOk());
        mockMvc.perform(delete("/api/datasets/{id}/columns/{key}", datasetId, "c0")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
        meta().andExpect(jsonPath("$.data.summaries", not(hasKey("c0"))));
    }

    @Test
    @DisplayName("열을 재정렬해도 요약은 key를 따라 유지된다")
    void reorderKeepsSummary() throws Exception {
        setSummary("c0", "{\"summary\":\"SUM\"}").andExpect(status().isOk());
        mockMvc.perform(patch("/api/datasets/{id}/columns/order", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[\"c1\",\"c0\"]}"))
                .andExpect(status().isOk());
        // c0은 이제 1번 위치지만 요약은 그대로다.
        meta().andExpect(jsonPath("$.data.columns[1].key").value("c0"))
                .andExpect(jsonPath("$.data.columns[1].summary").value("SUM"))
                .andExpect(jsonPath("$.data.summaries", hasKey("c0")));
    }
}
