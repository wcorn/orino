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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 열 푸터 요약 <b>값</b> 계산(#908). 기존 엔진의 열 집계(COLUMN_ALL)를 재사용해 응답
 * {@code summaries}에 값을 채운다.
 */
class DatasetSummaryValueTest extends ApiTestSupport {

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
        mockMvc.perform(post("/api/datasets/{id}/rows/bulk", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[[\"10\",\"3\"],[\"20\",\"2\"],[\"30\",\"4\"]]}"))
                .andExpect(status().isOk());
    }

    private void setSummary(String key, String fn) throws Exception {
        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/summary", datasetId, key)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"" + fn + "\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions meta() throws Exception {
        return mockMvc.perform(get("/api/datasets/{id}", datasetId)
                .header(HttpHeaders.AUTHORIZATION, authHeader));
    }

    @Test
    @DisplayName("SUM은 열 전체 합계를 낸다")
    void sumValue() throws Exception {
        setSummary("c0", "SUM");
        meta().andExpect(jsonPath("$.data.summaries.c0").value("60")); // 10+20+30
    }

    @Test
    @DisplayName("AVERAGE는 엔진 AVG로 매핑돼 평균을 낸다")
    void averageValue() throws Exception {
        setSummary("c0", "AVERAGE");
        meta().andExpect(jsonPath("$.data.summaries.c0").value("20")); // 60/3
    }

    @Test
    @DisplayName("COUNT는 숫자 셀만 센다")
    void countValue() throws Exception {
        setSummary("c0", "COUNT");
        meta().andExpect(jsonPath("$.data.summaries.c0").value("3"));
    }

    @Test
    @DisplayName("MIN·MAX")
    void minMaxValue() throws Exception {
        setSummary("c0", "MIN");
        meta().andExpect(jsonPath("$.data.summaries.c0").value("10"));
        setSummary("c0", "MAX");
        meta().andExpect(jsonPath("$.data.summaries.c0").value("30"));
    }

    @Test
    @DisplayName("셀을 고치면 요약 값이 따라 바뀐다")
    void valueUpdatesAfterEdit() throws Exception {
        setSummary("c0", "SUM");
        meta().andExpect(jsonPath("$.data.summaries.c0").value("60"));

        // 1행 단가 10 → 40. 합계 40+20+30 = 90.
        mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", datasetId, 0)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"40\",\"3\"]}"))
                .andExpect(status().isOk());

        meta().andExpect(jsonPath("$.data.summaries.c0").value("90"));
    }

    @Test
    @DisplayName("숫자가 아닌 셀이 섞여도 SUM은 숫자만 더한다")
    void sumIgnoresNonNumeric() throws Exception {
        mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", datasetId, 1)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"글자\",\"2\"]}"))
                .andExpect(status().isOk());
        setSummary("c0", "SUM");
        // 10 + (글자 무시) + 30 = 40.
        meta().andExpect(jsonPath("$.data.summaries.c0").value("40"));
    }

    @Test
    @DisplayName("요약 없는 데이터셋의 summaries는 빈 맵")
    void noSummaryEmptyMap() throws Exception {
        meta().andExpect(jsonPath("$.data.summaries").isMap())
                .andExpect(jsonPath("$.data.summaries.c0").doesNotExist());
    }
}
