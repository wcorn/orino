package ds.project.orino.planner.dataset.controller;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.dataset.repository.DatasetFormulaRepository;
import ds.project.orino.domain.planner.dataset.repository.DatasetRowRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 계산 열 — fill down과 행 추가 시 수식 승계(D10). 셀 단위 수식(D5)의 직접적 귀결. */
class DatasetCalculatedColumnTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DatasetFormulaRepository formulaRepository;
    @Autowired
    private DatasetRowRepository rowRepository;
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
                                {"columns":[{"key":"c0","label":"단가"},{"key":"c1","label":"수량"},
                                            {"key":"c2","label":"합계"}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        datasetId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        mockMvc.perform(post("/api/datasets/{id}/rows/bulk", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[[\"10\",\"3\",\"\"],[\"20\",\"2\",\"\"],[\"5\",\"4\",\"\"]]}"))
                .andExpect(status().isOk());
    }

    private ResultActions patchRow(int rowIndex, String cells) throws Exception {
        return mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", datasetId, rowIndex)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cells\":" + cells + "}"));
    }

    private ResultActions rows() throws Exception {
        return mockMvc.perform(get("/api/datasets/{id}/rows", datasetId)
                .header(HttpHeaders.AUTHORIZATION, authHeader));
    }

    private ResultActions fillDown(String key, int fromRowIndex) throws Exception {
        return mockMvc.perform(post("/api/datasets/{id}/columns/{key}/fill", datasetId, key)
                .param("fromRowIndex", String.valueOf(fromRowIndex))
                .header(HttpHeaders.AUTHORIZATION, authHeader));
    }

    @Test
    @DisplayName("fill down — 한 셀의 수식이 그 열 전체에 채워지고 각 행이 자기 행을 계산한다")
    void fillDownAppliesToWholeColumn() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]")
                .andExpect(jsonPath("$.data.cells[2]").value("30"));

        fillDown("c2", 0).andExpect(status().isOk());

        // 복사가 그냥 되는 이유가 D9 — 같은 행 참조엔 행 id가 없어 각 행이 자기 행을 본다.
        rows().andExpect(jsonPath("$.data.rows[0].cells[2]").value("30"))
                .andExpect(jsonPath("$.data.rows[1].cells[2]").value("40"))
                .andExpect(jsonPath("$.data.rows[2].cells[2]").value("20"));
    }

    @Test
    @DisplayName("fill down 후 모든 행이 같은 저장형을 갖는다")
    void fillDownStoresIdenticalRaw() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]").andExpect(status().isOk());
        fillDown("c2", 0).andExpect(status().isOk());

        assertThat(formulaRepository.countByDatasetIdAndColKey(datasetId, "c2")).isEqualTo(3);
        assertThat(formulaRepository.countDistinctRawByColumn(datasetId, "c2")).isEqualTo(1);
    }

    @Test
    @DisplayName("fill down 후에도 각 행 편집이 그 행만 다시 계산한다")
    void fillDownKeepsSameRowScoping() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]").andExpect(status().isOk());
        fillDown("c2", 0).andExpect(status().isOk());

        patchRow(1, "[\"100\",\"2\",\"=({단가} * {수량})\"]").andExpect(status().isOk());

        rows().andExpect(jsonPath("$.data.rows[0].cells[2]").value("30"))
                .andExpect(jsonPath("$.data.rows[1].cells[2]").value("200"))
                .andExpect(jsonPath("$.data.rows[2].cells[2]").value("20"));
    }

    @Test
    @DisplayName("fill down은 열 집계를 최종 값으로 한 번만 계산한다 — 반쯤 채워진 상태로 계산하면 안 된다")
    void fillDownComputesAggregateOnce() throws Exception {
        // 합계를 집계할 자리(총계)를 따로 만든다. 합계 열이 참조하는 단가·수량에 두면 순환이다.
        mockMvc.perform(post("/api/datasets/{id}/columns", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"총계\"}"))
                .andExpect(status().isCreated());

        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\",\"=SUM({합계})\"]")
                .andExpect(status().isOk());

        // 1행만 30이던 상태에서 채우면 30+40+20=90이 돼야 한다.
        fillDown("c2", 0).andExpect(status().isOk());

        rows().andExpect(jsonPath("$.data.rows[0].cells[3]").value("90"));
    }

    @Test
    @DisplayName("fill down — 수식 없는 셀에서 부르면 400")
    void fillDownWithoutFormula() throws Exception {
        fillDown("c2", 0).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("fill down — 없는 열·행이면 404")
    void fillDownNotFound() throws Exception {
        fillDown("c9", 0).andExpect(status().isNotFound());
        fillDown("c2", 99).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("행을 추가하면 계산 열이 수식을 물려준다 — 균일할 때만")
    void newRowInheritsUniformColumn() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]").andExpect(status().isOk());
        fillDown("c2", 0).andExpect(status().isOk());

        mockMvc.perform(post("/api/datasets/{id}/rows", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"7\",\"2\",\"\"]}"))
                .andExpect(status().isCreated());

        // 새 행도 수식을 받아 계산된다(빈 칸이 아니다).
        rows().andExpect(jsonPath("$.data.rows[3].cells[2]").value("14"))
                .andExpect(jsonPath("$.data.rows[3].formulas.c2").value("=({단가} * {수량})"));
    }

    @Test
    @DisplayName("열이 균일하지 않으면 물려주지 않는다 — 사용자가 의도한 게 아니다")
    void newRowDoesNotInheritMixedColumn() throws Exception {
        // 1행만 수식, 나머지는 값.
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]").andExpect(status().isOk());

        mockMvc.perform(post("/api/datasets/{id}/rows", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"7\",\"2\",\"\"]}"))
                .andExpect(status().isCreated());

        rows().andExpect(jsonPath("$.data.rows[3].cells[2]").value(""))
                .andExpect(jsonPath("$.data.rows[3].formulas.c2").doesNotExist());
    }

    @Test
    @DisplayName("수식이 섞여 있으면 물려주지 않는다 — 같은 열이라도 다른 수식이면")
    void mixedFormulasDoNotInherit() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]").andExpect(status().isOk());
        patchRow(1, "[\"20\",\"2\",\"={단가} + {수량}\"]").andExpect(status().isOk());
        patchRow(2, "[\"5\",\"4\",\"={단가} * {수량}\"]").andExpect(status().isOk());
        // 모든 행에 수식이 있지만 종류가 둘이다.
        assertThat(formulaRepository.countDistinctRawByColumn(datasetId, "c2")).isEqualTo(2);

        mockMvc.perform(post("/api/datasets/{id}/rows", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"7\",\"2\",\"\"]}"))
                .andExpect(status().isCreated());

        rows().andExpect(jsonPath("$.data.rows[3].formulas.c2").doesNotExist());
    }

    @Test
    @DisplayName("행을 추가하면 열 집계가 다시 계산된다")
    void newRowRecomputesAggregate() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"=SUM({단가})\"]")
                .andExpect(jsonPath("$.data.cells[2]").value("35")); // 10+20+5

        mockMvc.perform(post("/api/datasets/{id}/rows", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"100\",\"1\",\"\"]}"))
                .andExpect(status().isCreated());

        rows().andExpect(jsonPath("$.data.rows[0].cells[2]").value("135"));
    }

    @Test
    @DisplayName("맨 앞에 행을 끼워도 승계가 동작한다 — 행 번호가 밀리는 것과 무관")
    void inheritWorksOnInsertAtFront() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]").andExpect(status().isOk());
        fillDown("c2", 0).andExpect(status().isOk());

        mockMvc.perform(post("/api/datasets/{id}/rows", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"atIndex\":0,\"cells\":[\"2\",\"5\",\"\"]}"))
                .andExpect(status().isCreated());

        rows().andExpect(jsonPath("$.data.rows[0].cells[2]").value("10"))
                // 밀려난 기존 행들도 그대로다.
                .andExpect(jsonPath("$.data.rows[1].cells[2]").value("30"));
    }
}
