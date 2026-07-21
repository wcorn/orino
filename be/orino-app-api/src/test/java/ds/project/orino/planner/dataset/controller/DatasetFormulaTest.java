package ds.project.orino.planner.dataset.controller;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.dataset.entity.DatasetFormula;
import ds.project.orino.domain.planner.dataset.entity.FormulaRefKind;
import ds.project.orino.domain.planner.dataset.repository.DatasetFormulaRefRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 수식을 셀에 넣었을 때의 API 왕복. 별도 엔드포인트가 없고 {@code =}로 시작하는 값이면
 * 수식으로 보는 게 계약이라, 기존 셀 편집 API로 검증한다.
 */
class DatasetFormulaTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DatasetFormulaRepository formulaRepository;
    @Autowired
    private DatasetFormulaRefRepository refRepository;
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
                        .content("{\"rows\":[[\"10\",\"3\",\"\"],[\"20\",\"2\",\"\"]]}"))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions patchRow(int rowIndex, String cells)
            throws Exception {
        return mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", datasetId, rowIndex)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cells\":" + cells + "}"));
    }

    @Test
    @DisplayName("셀에 수식을 넣으면 계산된 값이 셀에 담기고 수식은 따로 저장된다")
    void formulaComputesAndStoresSeparately() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.edited.cells[2]").value("30"));

        // 읽기 경로는 그대로 — cells엔 계산된 값이 들어 있다.
        mockMvc.perform(get("/api/datasets/{id}/rows", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows[0].cells[2]").value("30"));

        long rowId = rowRepository.findByDatasetIdAndRowIndex(datasetId, 0).orElseThrow().getId();
        DatasetFormula f = formulaRepository.findByRowIdAndColKey(rowId, "c2").orElseThrow();
        // 저장형엔 label이 없다 — 이름을 바꿔도 안 깨진다.
        assertThat(f.getRaw()).isEqualTo("=({c0} * {c1})").doesNotContain("단가");
        assertThat(f.getError()).isNull();
    }

    @Test
    @DisplayName("같은 행 참조는 SAME_ROW로 기록된다 — 행 id를 안 쓴다")
    void sameRowRefsRecorded() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]").andExpect(status().isOk());

        long rowId = rowRepository.findByDatasetIdAndRowIndex(datasetId, 0).orElseThrow().getId();
        long formulaId = formulaRepository.findByRowIdAndColKey(rowId, "c2").orElseThrow().getId();
        assertThat(refRepository.findByFormulaId(formulaId))
                .allSatisfy(r -> {
                    assertThat(r.getToKind()).isEqualTo(FormulaRefKind.SAME_ROW);
                    assertThat(r.getToRowId()).isNull();
                })
                .extracting(r -> r.getToColKey())
                .containsExactlyInAnyOrder("c0", "c1");
    }

    @Test
    @DisplayName("같은 요청에서 바뀐 값을 수식이 즉시 반영한다")
    void formulaSeesValuesFromSameRequest() throws Exception {
        // 단가를 10 → 7로 바꾸면서 합계 수식을 넣는다. 옛 값 10이 아니라 7을 써야 한다.
        patchRow(0, "[\"7\",\"3\",\"={단가} * {수량}\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("21"));
    }

    @Test
    @DisplayName("열 집계는 전체 행을 본다")
    void columnAggregate() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"=SUM({단가})\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("30"));

        long rowId = rowRepository.findByDatasetIdAndRowIndex(datasetId, 0).orElseThrow().getId();
        long formulaId = formulaRepository.findByRowIdAndColKey(rowId, "c2").orElseThrow().getId();
        assertThat(refRepository.findByFormulaId(formulaId))
                .singleElement()
                .satisfies(r -> assertThat(r.getToKind()).isEqualTo(FormulaRefKind.COLUMN_ALL));
    }

    @Test
    @DisplayName("절대 참조는 그 행의 값을 본다")
    void absoluteRef() throws Exception {
        // 1행의 합계가 2행의 단가(20)를 가리킨다.
        patchRow(0, "[\"10\",\"3\",\"={단가}2\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("20"));

        long rowId = rowRepository.findByDatasetIdAndRowIndex(datasetId, 0).orElseThrow().getId();
        long row2 = rowRepository.findByDatasetIdAndRowIndex(datasetId, 1).orElseThrow().getId();
        long formulaId = formulaRepository.findByRowIdAndColKey(rowId, "c2").orElseThrow().getId();
        assertThat(refRepository.findByFormulaId(formulaId))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getToKind()).isEqualTo(FormulaRefKind.ABSOLUTE);
                    // 행 번호가 아니라 행 id로 굳었다.
                    assertThat(r.getToRowId()).isEqualTo(row2);
                });
    }

    @Test
    @DisplayName("O11 — 집계는 숫자 아닌 칸을 무시하고, 산술은 #VALUE!를 낸다")
    void aggregateIgnoresNonNumericButArithmeticFails() throws Exception {
        patchRow(1, "[\"글자\",\"2\",\"\"]").andExpect(status().isOk());

        // SUM은 '글자'를 무시하고 1행의 10만 더한다.
        patchRow(0, "[\"10\",\"3\",\"=SUM({단가})\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("10"));
        // COUNT도 숫자만 센다.
        patchRow(0, "[\"10\",\"3\",\"=COUNT({단가})\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("1"));
        // 산술은 무시하지 않는다.
        patchRow(1, "[\"글자\",\"2\",\"={단가} * {수량}\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("#VALUE!"));
    }

    @Test
    @DisplayName("0으로 나누면 #DIV/0!, 숫자 없는 열의 AVG도 #DIV/0!")
    void divisionErrors() throws Exception {
        patchRow(0, "[\"10\",\"0\",\"={단가} / {수량}\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("#DIV/0!"));

        patchRow(0, "[\"글자\",\"0\",\"\"]").andExpect(status().isOk());
        patchRow(1, "[\"글자\",\"0\",\"=AVG({단가})\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("#DIV/0!"));
    }

    @Test
    @DisplayName("에러는 셀 단위다 — 같은 열의 다른 행은 멀쩡하다")
    void errorIsPerCell() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("30"));
        patchRow(1, "[\"글자\",\"2\",\"={단가} * {수량}\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("#VALUE!"));

        mockMvc.perform(get("/api/datasets/{id}/rows", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows[0].cells[2]").value("30"))
                .andExpect(jsonPath("$.data.rows[1].cells[2]").value("#VALUE!"));
    }

    @Test
    @DisplayName("수식을 리터럴로 덮으면 수식과 참조가 지워진다")
    void overwritingFormulaRemovesIt() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]").andExpect(status().isOk());
        long rowId = rowRepository.findByDatasetIdAndRowIndex(datasetId, 0).orElseThrow().getId();
        long formulaId = formulaRepository.findByRowIdAndColKey(rowId, "c2").orElseThrow().getId();

        patchRow(0, "[\"10\",\"3\",\"직접입력\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("직접입력"));

        assertThat(formulaRepository.findByRowIdAndColKey(rowId, "c2")).isEmpty();
        assertThat(refRepository.findByFormulaId(formulaId)).isEmpty();
    }

    @Test
    @DisplayName("문법이 틀리면 400이고 아무것도 저장되지 않는다")
    void syntaxErrorRollsBack() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={없는열} * 2\"]")
                .andExpect(status().isBadRequest());

        long rowId = rowRepository.findByDatasetIdAndRowIndex(datasetId, 0).orElseThrow().getId();
        assertThat(formulaRepository.findByRowIdAndColKey(rowId, "c2")).isEmpty();
        // 같은 요청의 리터럴도 롤백된다.
        mockMvc.perform(get("/api/datasets/{id}/rows", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows[0].cells[2]").value(""));
    }

    @Test
    @DisplayName("열 이름을 바꿔도 수식이 안 깨진다 — 저장형에 이름이 없다")
    void renameDoesNotBreakFormula() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]").andExpect(status().isOk());

        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}", datasetId, "c0")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"가격\"}"))
                .andExpect(status().isOk());

        long rowId = rowRepository.findByDatasetIdAndRowIndex(datasetId, 0).orElseThrow().getId();
        assertThat(formulaRepository.findByRowIdAndColKey(rowId, "c2").orElseThrow().getRaw())
                .isEqualTo("=({c0} * {c1})");
        // 값도 그대로 남아 있다(재계산은 #813).
        mockMvc.perform(get("/api/datasets/{id}/rows", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows[0].cells[2]").value("30"));
    }

    @Test
    @DisplayName("열 범위 집계는 입력 시 집합으로 굳는다")
    void rangeSnapshot() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"=SUM({단가}:{수량})\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("35")); // 10+3+20+2

        long rowId = rowRepository.findByDatasetIdAndRowIndex(datasetId, 0).orElseThrow().getId();
        long formulaId = formulaRepository.findByRowIdAndColKey(rowId, "c2").orElseThrow().getId();
        // 범위가 아니라 열 목록으로 굳었다.
        assertThat(refRepository.findByFormulaId(formulaId))
                .extracting(r -> r.getToColKey())
                .containsExactlyInAnyOrder("c0", "c1");
        assertThat(formulaRepository.findByRowIdAndColKey(rowId, "c2").orElseThrow().getRaw())
                .isEqualTo("=SUM({c0}, {c1})");
    }

    @Test
    @DisplayName("여러 열에 수식을 동시에 넣을 수 있다")
    void multipleFormulasInOneRow() throws Exception {
        patchRow(0, "[\"10\",\"=2 + 1\",\"={단가} * {수량}\"]")
                .andExpect(jsonPath("$.data.edited.cells[1]").value("3"))
                // c1이 먼저 계산돼 c2가 그 값을 본다(열 순서).
                .andExpect(jsonPath("$.data.edited.cells[2]").value("30"));

        long rowId = rowRepository.findByDatasetIdAndRowIndex(datasetId, 0).orElseThrow().getId();
        assertThat(List.of(
                formulaRepository.findByRowIdAndColKey(rowId, "c1").isPresent(),
                formulaRepository.findByRowIdAndColKey(rowId, "c2").isPresent()))
                .containsExactly(true, true);
    }
}
