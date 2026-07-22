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
 * 표간 집계(R9 #915a-2). 요약 표가 {@code =SUM({도시!금액})}로 도시 표의 열을 집계한다.
 * 반응성(표간 전파)은 #915b — 저장 시 정확 계산까지.
 */
class DatasetCrossAggTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private long cityId;    // 대상: 이름 "도시", 금액(c0) = 100,200,300
    private long summaryId; // 참조: 합계(c0)

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);

        cityId = create("[{\"key\":\"c0\",\"label\":\"금액\"}]");
        mockMvc.perform(patch("/api/datasets/{id}/name", cityId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"도시\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/datasets/{id}/rows/bulk", cityId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[[\"100\"],[\"200\"],[\"300\"]]}"))
                .andExpect(status().isOk());

        summaryId = create("[{\"key\":\"c0\",\"label\":\"합계\"}]");
        mockMvc.perform(post("/api/datasets/{id}/rows/bulk", summaryId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[[\"\"]]}"))
                .andExpect(status().isOk());
    }

    private long create(String columnsJson) throws Exception {
        String body = mockMvc.perform(post("/api/datasets")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"columns\":" + columnsJson + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 요약 표 0행을 도시 참조와 함께 수정. */
    private ResultActions patchSummary(String formula) throws Exception {
        return mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", summaryId, 0)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cells\":[\"" + formula + "\"],\"tableRefs\":{\"도시\":" + cityId + "}}"));
    }

    private ResultActions summaryRows() throws Exception {
        return mockMvc.perform(get("/api/datasets/{id}/rows", summaryId)
                .header(HttpHeaders.AUTHORIZATION, authHeader));
    }

    @Test
    @DisplayName("요약이 도시 열의 SUM을 끌어온다 — 핵심")
    void crossSum() throws Exception {
        patchSummary("=SUM({도시!금액})")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.edited.cells[0]").value("600")); // 100+200+300

        summaryRows().andExpect(jsonPath("$.data.rows[0].cells[0]").value("600"))
                .andExpect(jsonPath("$.data.rows[0].formulas.c0").value("=SUM({도시!금액})"));
    }

    @Test
    @DisplayName("AVG·COUNT·MIN·MAX")
    void otherFuncs() throws Exception {
        patchSummary("=AVG({도시!금액})").andExpect(jsonPath("$.data.edited.cells[0]").value("200"));
        patchSummary("=COUNT({도시!금액})").andExpect(jsonPath("$.data.edited.cells[0]").value("3"));
        patchSummary("=MIN({도시!금액})").andExpect(jsonPath("$.data.edited.cells[0]").value("100"));
        patchSummary("=MAX({도시!금액})").andExpect(jsonPath("$.data.edited.cells[0]").value("300"));
    }

    @Test
    @DisplayName("표간 집계와 산술을 섞을 수 있다")
    void crossAggInArithmetic() throws Exception {
        patchSummary("=SUM({도시!금액}) * 2")
                .andExpect(jsonPath("$.data.edited.cells[0]").value("1200"));
    }

    @Test
    @DisplayName("표간 집계는 열 하나만 — 나열은 400")
    void crossAggMultiColumnRejected() throws Exception {
        patchSummary("=SUM({도시!금액}, {도시!금액})").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("tableRefs에 없는 표는 400")
    void unknownTableRejected() throws Exception {
        mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", summaryId, 0)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"=SUM({없는표!금액})\"],\"tableRefs\":{}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("표간 자동 재계산은 밖 — 도시 값이 바뀌어도 재저장 전엔 옛 합계(#915b)")
    void noCrossPropagationYet() throws Exception {
        patchSummary("=SUM({도시!금액})").andExpect(jsonPath("$.data.edited.cells[0]").value("600"));

        // 도시 1행 100 → 900. 합계는 900+200+300 = 1400이 되어야 하지만 전파가 없다.
        mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", cityId, 0)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"900\"]}"))
                .andExpect(status().isOk());

        summaryRows().andExpect(jsonPath("$.data.rows[0].cells[0]").value("600"));
    }
}
