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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 참조하던 열·행이 사라졌을 때. 엑셀처럼 삭제를 막지 않고 수식을 {@code #REF!}로 만든다.
 *
 * <p>열 삭제의 지연 정리(#800)·열 key 재사용 금지(#798)와 맞물리는 지점이라 함께 고정한다.
 */
class DatasetFormulaRefIntegrityTest extends ApiTestSupport {

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
                        .content("{\"rows\":[[\"10\",\"3\",\"\"],[\"20\",\"2\",\"\"]]}"))
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

    private ResultActions deleteColumn(String key) throws Exception {
        return mockMvc.perform(delete("/api/datasets/{id}/columns/{key}", datasetId, key)
                .header(HttpHeaders.AUTHORIZATION, authHeader));
    }

    @Test
    @DisplayName("참조하던 열을 지우면 수식이 #REF!가 된다 — 삭제를 막지 않는다")
    void deletingReferencedColumnMakesRef() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("30"));

        deleteColumn("c1").andExpect(status().isOk());

        // 열은 2개(단가·합계)가 되고, 합계는 #REF!.
        rows().andExpect(jsonPath("$.data.rows[0].cells", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.data.rows[0].cells[1]").value("#REF!"));

        long rowId = rowRepository.findByDatasetIdAndRowIndex(datasetId, 0).orElseThrow().getId();
        assertThat(formulaRepository.findByRowIdAndColKey(rowId, "c2").orElseThrow().getError())
                .isEqualTo("#REF!");
    }

    @Test
    @DisplayName("수식이 있던 열을 지우면 그 수식 자체가 사라진다 — 담길 셀이 없다")
    void deletingFormulaOwnColumnRemovesFormula() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]").andExpect(status().isOk());
        long rowId = rowRepository.findByDatasetIdAndRowIndex(datasetId, 0).orElseThrow().getId();
        assertThat(formulaRepository.findByRowIdAndColKey(rowId, "c2")).isPresent();

        deleteColumn("c2").andExpect(status().isOk());

        assertThat(formulaRepository.findByRowIdAndColKey(rowId, "c2")).isEmpty();
        assertThat(formulaRepository.countByDatasetId(datasetId)).isZero();
    }

    @Test
    @DisplayName("열 집계가 참조하던 열을 지워도 #REF!")
    void deletingAggregatedColumnMakesRef() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"=SUM({단가})\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("30"));

        deleteColumn("c0").andExpect(status().isOk());

        rows().andExpect(jsonPath("$.data.rows[0].cells[1]").value("#REF!"));
    }

    @Test
    @DisplayName("#REF!는 전파된다 — 그 셀을 참조하던 수식도 #REF!")
    void refErrorPropagates() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]").andExpect(status().isOk());
        // 합계를 참조하는 수식을 2행에 둔다.
        patchRow(1, "[\"20\",\"2\",\"={합계}1\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("30"));

        deleteColumn("c1").andExpect(status().isOk());

        // 1행 합계가 #REF!가 되고, 그걸 참조하던 2행도 따라 #REF!.
        rows().andExpect(jsonPath("$.data.rows[0].cells[1]").value("#REF!"))
                .andExpect(jsonPath("$.data.rows[1].cells[1]").value("#REF!"));
    }

    @Test
    @DisplayName("지연 정리(#800)와 무관하다 — 행에 남은 값이 #REF! 판정을 안 흔든다")
    void independentOfLazyPurge() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]").andExpect(status().isOk());
        deleteColumn("c1").andExpect(status().isOk());

        // #800대로 지운 열의 값은 행에 남아 있다.
        long rowId = rowRepository.findByDatasetIdAndRowIndex(datasetId, 0).orElseThrow().getId();
        assertThat(rowRepository.findById(rowId).orElseThrow().getCells()).contains("\"c1\"");
        // 그래도 수식은 #REF!다 — 판정은 columns 기준이다.
        rows().andExpect(jsonPath("$.data.rows[0].cells[1]").value("#REF!"));
    }

    @Test
    @DisplayName("key 재사용 금지(#798) 덕에 지운 열 자리에 새 열을 만들어도 #REF!가 안 되살아난다")
    void newColumnDoesNotResurrectRef() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]").andExpect(status().isOk());
        deleteColumn("c1").andExpect(status().isOk());

        // 새 열은 c3를 받는다(c1 재사용 안 함).
        mockMvc.perform(post("/api/datasets/{id}/columns", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.columns[2].key").value("c3"));

        // 수식은 여전히 c1을 가리키므로 #REF! 그대로 — 엉뚱한 새 열에 붙지 않는다.
        rows().andExpect(jsonPath("$.data.rows[0].cells[1]").value("#REF!"));
    }

    @Test
    @DisplayName("참조하던 행을 지우면 #REF! — 지운 뒤라야 드러난다")
    void deletingReferencedRowMakesRef() throws Exception {
        // 1행 합계가 2행 단가를 콕 집어 참조.
        patchRow(0, "[\"10\",\"3\",\"={단가}2\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("20"));

        mockMvc.perform(delete("/api/datasets/{id}/rows/{i}", datasetId, 1)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNoContent());

        rows().andExpect(jsonPath("$.data.rows[0].cells[2]").value("#REF!"));
    }

    @Test
    @DisplayName("행을 지우면 열 집계가 다시 계산된다 — 값이 하나 줄었다")
    void deletingRowRecomputesAggregate() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"=SUM({단가})\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("30")); // 10 + 20

        mockMvc.perform(delete("/api/datasets/{id}/rows/{i}", datasetId, 1)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNoContent());

        rows().andExpect(jsonPath("$.data.rows[0].cells[2]").value("10"));
    }

    @Test
    @DisplayName("열 순서를 바꿔도 참조가 안 깨진다 — key 바인딩이라 무관")
    void reorderDoesNotBreakRefs() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]").andExpect(status().isOk());

        mockMvc.perform(patch("/api/datasets/{id}/columns/order", datasetId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[\"c2\",\"c1\",\"c0\"]}"))
                .andExpect(status().isOk());

        // 합계가 이제 첫 칸이고 값도 그대로다.
        rows().andExpect(jsonPath("$.data.rows[0].cells[0]").value("30"))
                .andExpect(jsonPath("$.data.rows[0].formulas.c2").value("=({단가} * {수량})"));
    }

    @Test
    @DisplayName("#REF!가 된 수식도 표시형으로 볼 수 있다")
    void refFormulaStillDisplayable() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]").andExpect(status().isOk());
        deleteColumn("c1").andExpect(status().isOk());

        rows().andExpect(jsonPath("$.data.rows[0].formulas.c2").value("#REF!"));
    }
}
