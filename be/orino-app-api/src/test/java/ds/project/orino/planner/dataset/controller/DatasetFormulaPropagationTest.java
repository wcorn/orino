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

/** 참조하던 셀이 바뀌었을 때의 재계산과 순환 참조 거부. */
class DatasetFormulaPropagationTest extends ApiTestSupport {

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
                                            {"key":"c2","label":"합계"},{"key":"c3","label":"비고"}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        datasetId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        mockMvc.perform(post("/api/datasets/{id}/rows/bulk", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[[\"10\",\"3\",\"\",\"\"],[\"20\",\"2\",\"\",\"\"]]}"))
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

    @Test
    @DisplayName("참조하던 셀을 고치면 수식이 따라 바뀐다 — 이 이슈의 핵심")
    void editingReferencedCellRecomputesFormula() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\",\"\"]")
                .andExpect(jsonPath("$.data.cells[2]").value("30"));

        // 단가만 고친다. 합계는 손대지 않는다.
        patchRow(0, "[\"7\",\"3\",\"={단가} * {수량}\",\"\"]").andExpect(status().isOk());

        rows().andExpect(jsonPath("$.data.rows[0].cells[2]").value("21"));
    }

    @Test
    @DisplayName("연쇄로 번진다 — c2가 바뀌면 c2를 참조하던 c3도 따라 바뀐다")
    void propagatesTransitively() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\",\"\"]").andExpect(status().isOk());
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\",\"={합계} + 1\"]")
                .andExpect(jsonPath("$.data.cells[3]").value("31"));

        // 단가 → 합계 → 비고 로 두 단계 번져야 한다.
        patchRow(0, "[\"5\",\"3\",\"={단가} * {수량}\",\"={합계} + 1\"]")
                .andExpect(status().isOk());

        rows().andExpect(jsonPath("$.data.rows[0].cells[2]").value("15"))
                .andExpect(jsonPath("$.data.rows[0].cells[3]").value("16"));
    }

    @Test
    @DisplayName("SAME_ROW 전파는 행을 넘지 않는다 — 계산 열의 편집이 다른 행을 안 건드린다")
    void sameRowPropagationStaysInRow() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\",\"\"]").andExpect(status().isOk());
        patchRow(1, "[\"20\",\"2\",\"={단가} * {수량}\",\"\"]").andExpect(status().isOk());
        rows().andExpect(jsonPath("$.data.rows[0].cells[2]").value("30"))
                .andExpect(jsonPath("$.data.rows[1].cells[2]").value("40"));

        // 1행의 단가만 고친다.
        patchRow(0, "[\"1\",\"3\",\"={단가} * {수량}\",\"\"]").andExpect(status().isOk());

        rows().andExpect(jsonPath("$.data.rows[0].cells[2]").value("3"))
                // 2행은 그대로여야 한다.
                .andExpect(jsonPath("$.data.rows[1].cells[2]").value("40"));
    }

    @Test
    @DisplayName("COLUMN_ALL 전파는 그 열의 아무 행이 바뀌어도 걸린다")
    void columnAggregateRecomputesOnAnyRow() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"=SUM({단가})\",\"\"]")
                .andExpect(jsonPath("$.data.cells[2]").value("30"));

        // 2행의 단가를 고치면 1행의 집계가 따라 바뀐다.
        patchRow(1, "[\"5\",\"2\",\"\",\"\"]").andExpect(status().isOk());

        rows().andExpect(jsonPath("$.data.rows[0].cells[2]").value("15"));
    }

    @Test
    @DisplayName("절대 참조는 그 행이 바뀔 때 따라 바뀐다")
    void absoluteRefRecomputes() throws Exception {
        // 1행 비고가 2행 단가를 가리킨다.
        patchRow(0, "[\"10\",\"3\",\"\",\"={단가}2\"]")
                .andExpect(jsonPath("$.data.cells[3]").value("20"));

        patchRow(1, "[\"99\",\"2\",\"\",\"\"]").andExpect(status().isOk());

        rows().andExpect(jsonPath("$.data.rows[0].cells[3]").value("99"));
    }

    @Test
    @DisplayName("에러도 전파된다 — 참조가 숫자가 아니게 되면 #VALUE!")
    void errorPropagates() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\",\"\"]")
                .andExpect(jsonPath("$.data.cells[2]").value("30"));

        patchRow(0, "[\"글자\",\"3\",\"={단가} * {수량}\",\"\"]").andExpect(status().isOk());

        rows().andExpect(jsonPath("$.data.rows[0].cells[2]").value("#VALUE!"));

        // 다시 숫자로 되돌리면 복구된다.
        patchRow(0, "[\"4\",\"3\",\"={단가} * {수량}\",\"\"]").andExpect(status().isOk());
        rows().andExpect(jsonPath("$.data.rows[0].cells[2]").value("12"));
        long rowId = rowRepository.findByDatasetIdAndRowIndex(datasetId, 0).orElseThrow().getId();
        assertThat(formulaRepository.findByRowIdAndColKey(rowId, "c2").orElseThrow().getError())
                .isNull();
    }

    @Test
    @DisplayName("자기 자신을 참조하면 409")
    void selfReferenceRejected() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={합계} + 1\",\"\"]")
                .andExpect(status().isConflict());

        long rowId = rowRepository.findByDatasetIdAndRowIndex(datasetId, 0).orElseThrow().getId();
        assertThat(formulaRepository.findByRowIdAndColKey(rowId, "c2")).isEmpty();
    }

    @Test
    @DisplayName("두 셀이 서로를 참조하면 409 — 두 번째를 저장할 때 막힌다")
    void mutualReferenceRejected() throws Exception {
        // c2 = c3 + 1 은 아직 순환이 아니다(c3엔 수식이 없다).
        patchRow(0, "[\"10\",\"3\",\"={비고} + 1\",\"\"]").andExpect(status().isOk());
        // c3 = c2 + 1 을 넣는 순간 c2 → c3 → c2 가 된다.
        patchRow(0, "[\"10\",\"3\",\"={비고} + 1\",\"={합계} + 1\"]")
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("열 집계가 자기 열을 물면 409 — SUM이 그 열의 수식에 닿는다")
    void aggregateOverOwnColumnRejected() throws Exception {
        // c2 = SUM(c2) 는 c2의 수식(자기 자신)에 닿는다.
        patchRow(0, "[\"10\",\"3\",\"=SUM({합계})\",\"\"]")
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("순환이 아니면 같은 열을 참조해도 통과한다")
    void sameColumnAcrossRowsIsNotCycle() throws Exception {
        // 1행 비고 = 2행 단가. 순환이 아니다.
        patchRow(0, "[\"10\",\"3\",\"\",\"={단가}2\"]").andExpect(status().isOk());
        // 2행 합계 = 같은 행 단가*수량. 위와 무관.
        patchRow(1, "[\"20\",\"2\",\"={단가} * {수량}\",\"\"]")
                .andExpect(jsonPath("$.data.cells[2]").value("40"));
    }

    @Test
    @DisplayName("조회 응답의 수식을 그대로 돌려주면 살아남는다 — 값을 돌려주면 지워진다")
    void formulaSurvivesEchoButNotValue() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\",\"\"]").andExpect(status().isOk());

        // 조회 응답이 표시형 수식을 준다.
        rows().andExpect(jsonPath("$.data.rows[0].cells[2]").value("30"))
                .andExpect(jsonPath("$.data.rows[0].formulas.c2").value("=({단가} * {수량})"))
                // 수식 없는 셀은 담기지 않는다.
                .andExpect(jsonPath("$.data.rows[0].formulas.c0").doesNotExist());

        // 그 수식을 그대로 돌려주면 살아남는다.
        patchRow(0, "[\"2\",\"3\",\"=({단가} * {수량})\",\"\"]")
                .andExpect(jsonPath("$.data.cells[2]").value("6"));
        rows().andExpect(jsonPath("$.data.rows[0].formulas.c2").exists());

        // 계산된 값을 돌려주면 사용자가 직접 입력한 것으로 보고 수식을 지운다.
        patchRow(0, "[\"2\",\"3\",\"6\",\"\"]").andExpect(status().isOk());
        rows().andExpect(jsonPath("$.data.rows[0].formulas.c2").doesNotExist());
    }

    @Test
    @DisplayName("표시형 수식은 현재 열 이름을 따라간다")
    void displayFormulaFollowsRename() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\",\"\"]").andExpect(status().isOk());

        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}", datasetId, "c0")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"가격\"}"))
                .andExpect(status().isOk());

        rows().andExpect(jsonPath("$.data.rows[0].formulas.c2").value("=({가격} * {수량})"));
    }
}
