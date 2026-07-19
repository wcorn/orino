package ds.project.orino.planner.dataset.controller;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.dataset.entity.DatasetFormula;
import ds.project.orino.domain.planner.dataset.repository.DatasetCellStyleRepository;
import ds.project.orino.domain.planner.dataset.repository.DatasetFormulaRepository;
import ds.project.orino.domain.planner.dataset.repository.DatasetMergeRepository;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DatasetControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DatasetRowRepository datasetRowRepository;
    @Autowired
    private DatasetFormulaRepository datasetFormulaRepository;
    @Autowired
    private DatasetCellStyleRepository datasetCellStyleRepository;
    @Autowired
    private DatasetMergeRepository datasetMergeRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        memberRepository.save(MemberFixture.create("other", "password"));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    /** 2열 dataset을 만들고 id를 반환한다. */
    private long createDataset() throws Exception {
        String body = mockMvc.perform(post("/api/datasets")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columns":[{"key":"c0","label":"과목"},{"key":"c1","label":"점수"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rowCount").value(0))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private void bulk(long id, String rowsJson) throws Exception {
        mockMvc.perform(post("/api/datasets/{id}/rows/bulk", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":" + rowsJson + "}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST datasets - 생성 시 columns/rowCount 반환, 이후 메타 조회")
    void create_and_meta() throws Exception {
        long id = createDataset();

        mockMvc.perform(get("/api/datasets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.columns", hasSize(2)))
                .andExpect(jsonPath("$.data.columns[0].label").value("과목"))
                .andExpect(jsonPath("$.data.rowCount").value(0));
    }

    @Test
    @DisplayName("POST datasets - columns 비면 400")
    void create_empty_columns_400() throws Exception {
        mockMvc.perform(post("/api/datasets")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"columns\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("bulk 추가 후 rows를 row_index 순으로 조회한다")
    void bulk_and_list() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"],[\"운영체제\",\"78\"]]");

        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows", hasSize(2)))
                .andExpect(jsonPath("$.data.rows[0].rowIndex").value(0))
                .andExpect(jsonPath("$.data.rows[0].cells[0]").value("네트워크"))
                .andExpect(jsonPath("$.data.rows[1].rowIndex").value(1))
                .andExpect(jsonPath("$.data.rows[1].cells[1]").value("78"));

        mockMvc.perform(get("/api/datasets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rowCount").value(2));
    }

    @Test
    @DisplayName("GET rows - offset/limit 페이지네이션")
    void rows_pagination() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"0\",\"a\"],[\"1\",\"b\"],[\"2\",\"c\"],[\"3\",\"d\"],[\"4\",\"e\"]]");

        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .param("offset", "1").param("limit", "2")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.offset").value(1))
                .andExpect(jsonPath("$.data.rows", hasSize(2)))
                .andExpect(jsonPath("$.data.rows[0].rowIndex").value(1))
                .andExpect(jsonPath("$.data.rows[1].rowIndex").value(2));
    }

    @Test
    @DisplayName("PATCH row - 셀 수정이 반영된다")
    void update_row() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");

        mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", id, 0)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"네트워크\",\"100\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cells[1]").value("100"));

        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows[0].cells[1]").value("100"));
    }

    @Test
    @DisplayName("PATCH row - 없는 rowIndex면 404")
    void update_missing_row_404() throws Exception {
        long id = createDataset();
        mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", id, 5)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"x\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST row - atIndex 삽입 시 뒤 행이 밀린다")
    void insert_row_shifts() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"A\",\"1\"],[\"B\",\"2\"]]");

        mockMvc.perform(post("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"atIndex\":1,\"cells\":[\"NEW\",\"9\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rowIndex").value(1));

        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows", hasSize(3)))
                .andExpect(jsonPath("$.data.rows[0].cells[0]").value("A"))
                .andExpect(jsonPath("$.data.rows[1].cells[0]").value("NEW"))
                .andExpect(jsonPath("$.data.rows[2].cells[0]").value("B"));
    }

    @Test
    @DisplayName("POST row - atIndex 생략 시 끝에 append")
    void insert_row_append() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"A\",\"1\"]]");

        mockMvc.perform(post("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"B\",\"2\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rowIndex").value(1));
    }

    @Test
    @DisplayName("DELETE row - 삭제 후 뒤 행이 당겨지고 rowCount 감소")
    void delete_row_shifts() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"A\",\"1\"],[\"B\",\"2\"],[\"C\",\"3\"]]");

        mockMvc.perform(delete("/api/datasets/{id}/rows/{i}", id, 0)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows", hasSize(2)))
                .andExpect(jsonPath("$.data.rows[0].rowIndex").value(0))
                .andExpect(jsonPath("$.data.rows[0].cells[0]").value("B"))
                .andExpect(jsonPath("$.data.rows[1].cells[0]").value("C"));

        mockMvc.perform(get("/api/datasets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rowCount").value(2));
    }

    /** rowIndex 순으로 각 행의 id를 뽑는다. */
    private java.util.List<Long> rowIds(long id) throws Exception {
        String body = mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        java.util.List<Number> raw = JsonPath.read(body, "$.data.rows[*].id");
        return raw.stream().map(Number::longValue).toList();
    }

    @Test
    @DisplayName("dataset 삭제 시 수식도 cascade로 함께 지워진다")
    void delete_dataset_cascades_formulas() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");
        long rowId = rowIds(id).get(0);
        datasetFormulaRepository.save(new DatasetFormula(id, rowId, "c1", "=c0"));
        org.assertj.core.api.Assertions.assertThat(
                datasetFormulaRepository.countByDatasetId(id)).isEqualTo(1);

        mockMvc.perform(delete("/api/datasets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(
                datasetFormulaRepository.countByDatasetId(id)).isZero();
    }

    @Test
    @DisplayName("행 삭제 시 그 행의 수식도 cascade로 함께 지워진다")
    void delete_row_cascades_formula() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"A\",\"1\"],[\"B\",\"2\"]]");
        java.util.List<Long> ids = rowIds(id);
        datasetFormulaRepository.save(new DatasetFormula(id, ids.get(0), "c1", "=c0"));
        datasetFormulaRepository.save(new DatasetFormula(id, ids.get(1), "c1", "=c0"));

        mockMvc.perform(delete("/api/datasets/{id}/rows/{i}", id, 0)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNoContent());

        // 지운 행의 수식만 사라지고 남은 행 것은 유지된다.
        org.assertj.core.api.Assertions.assertThat(
                datasetFormulaRepository.findByRowIdAndColKey(ids.get(0), "c1")).isEmpty();
        org.assertj.core.api.Assertions.assertThat(
                datasetFormulaRepository.findByRowIdAndColKey(ids.get(1), "c1")).isPresent();
    }

    @Test
    @DisplayName("GET rows - 행 id를 함께 반환한다")
    void rows_expose_id() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"A\",\"1\"],[\"B\",\"2\"]]");

        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[0].id").isNumber())
                .andExpect(jsonPath("$.data.rows[1].id").isNumber());

        // PATCH 응답에도 같은 id가 실린다.
        long firstId = rowIds(id).get(0);
        mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", id, 0)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"A수정\",\"1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(firstId));
    }

    @Test
    @DisplayName("행을 앞에 끼워 rowIndex가 밀려도 기존 행의 id는 그대로다")
    void row_id_survives_insert_shift() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"A\",\"1\"],[\"B\",\"2\"]]");
        java.util.List<Long> before = rowIds(id);

        // 맨 앞에 삽입 — 기존 두 행의 rowIndex는 0,1 → 1,2로 밀린다.
        mockMvc.perform(post("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"atIndex\":0,\"cells\":[\"NEW\",\"9\"]}"))
                .andExpect(status().isCreated());

        java.util.List<Long> after = rowIds(id);
        org.assertj.core.api.Assertions.assertThat(after).hasSize(3);
        // rowIndex는 밀렸지만 id는 보존된다 — 수식 참조가 id에 묶여야 하는 이유.
        org.assertj.core.api.Assertions.assertThat(after.subList(1, 3))
                .as("삽입 후에도 기존 행 id가 순서대로 유지돼야 한다")
                .isEqualTo(before);

        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows[1].rowIndex").value(1))
                .andExpect(jsonPath("$.data.rows[1].cells[0]").value("A"));
    }

    @Test
    @DisplayName("행을 지워 rowIndex가 당겨져도 남은 행의 id는 그대로다")
    void row_id_survives_delete_shift() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"A\",\"1\"],[\"B\",\"2\"],[\"C\",\"3\"]]");
        java.util.List<Long> before = rowIds(id);

        mockMvc.perform(delete("/api/datasets/{id}/rows/{i}", id, 0)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNoContent());

        // B, C의 rowIndex는 1,2 → 0,1로 당겨지지만 id는 그대로다.
        org.assertj.core.api.Assertions.assertThat(rowIds(id))
                .isEqualTo(before.subList(1, 3));
    }

    @Test
    @DisplayName("셀을 수정해도 행 id는 바뀌지 않는다")
    void row_id_survives_update() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"A\",\"1\"]]");
        long before = rowIds(id).get(0);

        mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", id, 0)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"수정됨\",\"9\"]}"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(rowIds(id).get(0)).isEqualTo(before);
    }

    /** 3열(과목/점수/비고) dataset을 만들고 1행을 채운다. */
    private long createThreeColumnDataset() throws Exception {
        String body = mockMvc.perform(post("/api/datasets")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columns":[{"key":"c0","label":"과목"},{"key":"c1","label":"점수"},
                                            {"key":"c2","label":"비고"}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        bulk(id, "[[\"네트워크\",\"92\",\"재수강\"]]");
        return id;
    }

    @Test
    @DisplayName("PATCH columns/order - 순서가 바뀌고 값이 열을 따라간다")
    void reorder_columns() throws Exception {
        long id = createThreeColumnDataset();

        // 비고를 맨 앞으로: c2, c0, c1
        mockMvc.perform(patch("/api/datasets/{id}/columns/order", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[\"c2\",\"c0\",\"c1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[0].label").value("비고"))
                .andExpect(jsonPath("$.data.columns[1].label").value("과목"))
                .andExpect(jsonPath("$.data.columns[2].label").value("점수"));

        // 값이 새 순서를 따라온다 — 위치가 아니라 key에 묶여 있으므로.
        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows[0].cells[0]").value("재수강"))
                .andExpect(jsonPath("$.data.rows[0].cells[1]").value("네트워크"))
                .andExpect(jsonPath("$.data.rows[0].cells[2]").value("92"));
    }

    @Test
    @DisplayName("열 순서 변경은 행을 건드리지 않는다")
    void reorder_does_not_touch_rows() throws Exception {
        long id = createThreeColumnDataset();
        String before = datasetRowRepository.findByDatasetIdAndRowIndex(id, 0)
                .orElseThrow().getCells();

        mockMvc.perform(patch("/api/datasets/{id}/columns/order", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[\"c2\",\"c1\",\"c0\"]}"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(
                        datasetRowRepository.findByDatasetIdAndRowIndex(id, 0).orElseThrow().getCells())
                .as("순서 변경은 O(1)이어야 하므로 행 JSON이 그대로여야 한다")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("PATCH columns/order - 열 집합이 다르면 400")
    void reorder_rejects_wrong_key_set() throws Exception {
        long id = createThreeColumnDataset();

        // 하나 빠짐
        mockMvc.perform(patch("/api/datasets/{id}/columns/order", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[\"c1\",\"c0\"]}"))
                .andExpect(status().isBadRequest());
        // 없는 key
        mockMvc.perform(patch("/api/datasets/{id}/columns/order", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[\"c0\",\"c1\",\"c9\"]}"))
                .andExpect(status().isBadRequest());
        // 중복 key — 개수는 맞지만 집합이 다르다
        mockMvc.perform(patch("/api/datasets/{id}/columns/order", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[\"c0\",\"c0\",\"c1\"]}"))
                .andExpect(status().isBadRequest());
        // 빈 배열
        mockMvc.perform(patch("/api/datasets/{id}/columns/order", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH columns/order - 타인 dataset이면 404")
    void reorder_other_member_404() throws Exception {
        long id = createThreeColumnDataset();
        String otherAuth = "Bearer " + AuthFixture.loginAndGetAccessToken(
                mockMvc, "other", "password");

        mockMvc.perform(patch("/api/datasets/{id}/columns/order", id)
                        .header(HttpHeaders.AUTHORIZATION, otherAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[\"c2\",\"c1\",\"c0\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("순서를 바꿔도 rename·삭제가 key 기준으로 계속 동작한다")
    void reorder_then_rename_and_delete() throws Exception {
        long id = createThreeColumnDataset();
        mockMvc.perform(patch("/api/datasets/{id}/columns/order", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[\"c2\",\"c0\",\"c1\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}", id, "c0")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"과목명\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[1].label").value("과목명"));

        mockMvc.perform(delete("/api/datasets/{id}/columns/{key}", id, "c2")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[0].label").value("과목명"));

        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows[0].cells[0]").value("네트워크"))
                .andExpect(jsonPath("$.data.rows[0].cells[1]").value("92"));
    }

    @Test
    @DisplayName("DELETE column - 열이 빠지고 남은 열 값은 유지된다")
    void delete_column() throws Exception {
        long id = createDataset();
        mockMvc.perform(post("/api/datasets/{id}/columns", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"비고\"}"))
                .andExpect(status().isCreated());
        bulk(id, "[[\"네트워크\",\"92\",\"재수강\"]]");

        // 가운데 열(c1) 삭제 — 뒤 열 값이 앞으로 당겨져야 한다.
        mockMvc.perform(delete("/api/datasets/{id}/columns/{key}", id, "c1")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns", hasSize(2)))
                .andExpect(jsonPath("$.data.columns[0].key").value("c0"))
                .andExpect(jsonPath("$.data.columns[1].key").value("c2"));

        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows[0].cells", hasSize(2)))
                .andExpect(jsonPath("$.data.rows[0].cells[0]").value("네트워크"))
                .andExpect(jsonPath("$.data.rows[0].cells[1]").value("재수강"));
    }

    @Test
    @DisplayName("열 삭제는 행을 건드리지 않고, 남은 값은 다음 수정 때 정리된다")
    void delete_column_purges_lazily() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");

        mockMvc.perform(delete("/api/datasets/{id}/columns/{key}", id, "c1")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());

        // O(1) — 삭제 시점엔 행 JSON이 그대로다(지운 c1 값이 남아 있음).
        org.assertj.core.api.Assertions.assertThat(
                        datasetRowRepository.findByDatasetIdAndRowIndex(id, 0).orElseThrow().getCells())
                .contains("\"c1\"").contains("92");

        // 다만 API로는 드러나지 않는다.
        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows[0].cells", hasSize(1)))
                .andExpect(jsonPath("$.data.rows[0].cells[0]").value("네트워크"));

        // 그 행을 수정하면 맵이 새로 만들어지며 남은 값이 사라진다.
        mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", id, 0)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"운영체제\"]}"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(
                        datasetRowRepository.findByDatasetIdAndRowIndex(id, 0).orElseThrow().getCells())
                .doesNotContain("\"c1\"").doesNotContain("92");
    }

    @Test
    @DisplayName("열을 지운 뒤 추가해도 지운 key가 재사용되지 않아 옛 값이 되살아나지 않는다")
    void delete_then_add_does_not_resurrect_values() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");

        mockMvc.perform(delete("/api/datasets/{id}/columns/{key}", id, "c1")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/datasets/{id}/columns", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"새 열\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.columns[1].key").value("c2"));

        // 행엔 c1="92"가 남아 있지만 새 열은 c2라 빈 값으로 보여야 한다.
        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows[0].cells[0]").value("네트워크"))
                .andExpect(jsonPath("$.data.rows[0].cells[1]").value(""));
    }

    @Test
    @DisplayName("DELETE column - 마지막 열은 400, 없는 key는 404, 타인 dataset은 404")
    void delete_column_validation() throws Exception {
        long id = createDataset();

        mockMvc.perform(delete("/api/datasets/{id}/columns/{key}", id, "c9")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound());

        String otherAuth = "Bearer " + AuthFixture.loginAndGetAccessToken(
                mockMvc, "other", "password");
        mockMvc.perform(delete("/api/datasets/{id}/columns/{key}", id, "c0")
                        .header(HttpHeaders.AUTHORIZATION, otherAuth))
                .andExpect(status().isNotFound());

        // 2열 중 하나를 지우면 남은 1열은 못 지운다.
        mockMvc.perform(delete("/api/datasets/{id}/columns/{key}", id, "c1")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/datasets/{id}/columns/{key}", id, "c0")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST column - 열이 끝에 추가되고 기존 행 값은 그대로, 새 열은 빈 값")
    void add_column() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");

        mockMvc.perform(post("/api/datasets/{id}/columns", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"비고\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.columns", hasSize(3)))
                .andExpect(jsonPath("$.data.columns[2].key").value("c2"))
                .andExpect(jsonPath("$.data.columns[2].label").value("비고"))
                .andExpect(jsonPath("$.data.rowCount").value(1));

        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows[0].cells", hasSize(3)))
                .andExpect(jsonPath("$.data.rows[0].cells[0]").value("네트워크"))
                .andExpect(jsonPath("$.data.rows[0].cells[1]").value("92"))
                .andExpect(jsonPath("$.data.rows[0].cells[2]").value(""));
    }

    @Test
    @DisplayName("POST column - atIndex를 주면 그 위치에 끼우고 기존 행 값은 그대로")
    void add_column_at_index() throws Exception {
        long id = createDataset(); // c0(과목), c1(점수)
        bulk(id, "[[\"네트워크\",\"92\"]]");

        // 0번 자리에 삽입 → 새 열이 맨 앞, 기존 열은 뒤로 밀린다(key는 불변).
        mockMvc.perform(post("/api/datasets/{id}/columns", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"비고\",\"atIndex\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.columns", hasSize(3)))
                .andExpect(jsonPath("$.data.columns[0].key").value("c2"))
                .andExpect(jsonPath("$.data.columns[0].label").value("비고"))
                .andExpect(jsonPath("$.data.columns[1].key").value("c0"))
                .andExpect(jsonPath("$.data.columns[2].key").value("c1"));

        // cells는 새 열 순서로 투영된다 — 새 열은 빈 값, 기존 값은 뒤 자리로.
        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows[0].cells[0]").value(""))
                .andExpect(jsonPath("$.data.rows[0].cells[1]").value("네트워크"))
                .andExpect(jsonPath("$.data.rows[0].cells[2]").value("92"));
    }

    @Test
    @DisplayName("열 추가는 행을 건드리지 않는다(저장된 맵에 새 key가 안 생김)")
    void add_column_does_not_touch_rows() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");
        String before = datasetRowRepository.findByDatasetIdAndRowIndex(id, 0)
                .orElseThrow().getCells();

        mockMvc.perform(post("/api/datasets/{id}/columns", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"비고\"}"))
                .andExpect(status().isCreated());

        String after = datasetRowRepository.findByDatasetIdAndRowIndex(id, 0)
                .orElseThrow().getCells();
        org.assertj.core.api.Assertions.assertThat(after)
                .as("열 추가는 O(1)이어야 하므로 행 JSON이 그대로여야 한다")
                .isEqualTo(before)
                .doesNotContain("c2");
    }

    @Test
    @DisplayName("열 key는 재사용되지 않는다 - 연속 추가 시 번호가 계속 올라간다")
    void add_column_never_reuses_key() throws Exception {
        long id = createDataset();

        for (String label : new String[]{"셋째", "넷째"}) {
            mockMvc.perform(post("/api/datasets/{id}/columns", id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"label\":\"" + label + "\"}"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/datasets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.columns", hasSize(4)))
                .andExpect(jsonPath("$.data.columns[2].key").value("c2"))
                .andExpect(jsonPath("$.data.columns[3].key").value("c3"));
    }

    @Test
    @DisplayName("POST column - label이 비면 400, 타인 dataset이면 404")
    void add_column_validation() throws Exception {
        long id = createDataset();
        // label을 비우면 거부가 아니라 서버가 기본 이름을 발급한다.
        mockMvc.perform(post("/api/datasets/{id}/columns", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.columns[2].label").value("열 3"));

        String otherAuth = "Bearer " + AuthFixture.loginAndGetAccessToken(
                mockMvc, "other", "password");
        mockMvc.perform(post("/api/datasets/{id}/columns", id)
                        .header(HttpHeaders.AUTHORIZATION, otherAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"침입\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("생성 - Import한 중복 헤더는 거부하지 않고 자동 구분한다")
    void create_deduplicates_labels() throws Exception {
        // 같은 이름 헤더를 가진 스프레드시트 Import를 막으면 안 된다.
        String body = mockMvc.perform(post("/api/datasets")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columns":[{"key":"c0","label":"점수"},{"key":"c1","label":"점수"},
                                            {"key":"c2","label":"점수"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.columns[0].label").value("점수"))
                .andExpect(jsonPath("$.data.columns[1].label").value("점수 (2)"))
                .andExpect(jsonPath("$.data.columns[2].label").value("점수 (3)"))
                .andReturn().getResponse().getContentAsString();

        // 순서와 key 대응은 그대로여야 한다.
        long id = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        mockMvc.perform(get("/api/datasets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.columns[1].key").value("c1"));
    }

    @Test
    @DisplayName("생성 - 자동 구분한 이름이 다른 헤더와 또 겹치면 번호를 올린다")
    void create_dedup_avoids_secondary_collision() throws Exception {
        mockMvc.perform(post("/api/datasets")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columns":[{"key":"c0","label":"점수"},{"key":"c1","label":"점수 (2)"},
                                            {"key":"c2","label":"점수"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.columns[0].label").value("점수"))
                .andExpect(jsonPath("$.data.columns[1].label").value("점수 (2)"))
                // "점수 (2)"가 이미 있으므로 "점수 (3)"으로
                .andExpect(jsonPath("$.data.columns[2].label").value("점수 (3)"));
    }

    @Test
    @DisplayName("열 추가 - 서버가 발급한 기본 이름은 삭제 후에도 중복되지 않는다")
    void generated_label_never_collides_after_delete() throws Exception {
        long id = createDataset(); // 과목(c0), 점수(c1)
        // 열 3 추가 → 열1/열2 아님. 기존 label은 과목/점수라 "열 3"이 나온다.
        mockMvc.perform(post("/api/datasets/{id}/columns", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.columns[2].label").value("열 3"));

        // 가운데 열을 지우면 열 개수가 2로 줄지만, 기본 이름이 "열 3"으로 되돌아가면 안 된다.
        mockMvc.perform(delete("/api/datasets/{id}/columns/{key}", id, "c1")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/datasets/{id}/columns", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                // 남은 label이 과목/열 3이므로 "열 3"을 피해 "열 4"
                .andExpect(jsonPath("$.data.columns[2].label").value("열 4"));

        mockMvc.perform(get("/api/datasets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.columns[*].label",
                        org.hamcrest.Matchers.containsInAnyOrder("과목", "열 3", "열 4")));
    }

    @Test
    @DisplayName("열 추가 - 사람이 지정한 이름이 겹치면 409")
    void add_column_duplicate_label_409() throws Exception {
        long id = createDataset(); // 과목, 점수
        mockMvc.perform(post("/api/datasets/{id}/columns", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"점수\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("rename - 다른 열과 겹치면 409, 자기 이름 그대로는 허용")
    void rename_duplicate_label_409() throws Exception {
        long id = createDataset(); // 과목(c0), 점수(c1)

        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}", id, "c1")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"과목\"}"))
                .andExpect(status().isConflict());

        // 자기 자신과의 비교는 충돌이 아니다.
        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}", id, "c1")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"점수\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("추가한 열에 값을 쓰면 새 key로 저장된다")
    void write_to_added_column() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");
        mockMvc.perform(post("/api/datasets/{id}/columns", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"비고\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", id, 0)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"네트워크\",\"92\",\"재수강\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cells[2]").value("재수강"));

        org.assertj.core.api.Assertions.assertThat(
                        datasetRowRepository.findByDatasetIdAndRowIndex(id, 0).orElseThrow().getCells())
                .contains("\"c2\"").contains("재수강");
    }

    @Test
    @DisplayName("cells는 열 key 기반 맵으로 저장된다(API 계약은 위치 배열 유지)")
    void cells_stored_as_key_map() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");

        String stored = datasetRowRepository.findByDatasetIdAndRowIndex(id, 0)
                .orElseThrow().getCells();
        org.assertj.core.api.Assertions.assertThat(stored)
                .contains("\"c0\"").contains("\"c1\"")
                .contains("네트워크").contains("92")
                .doesNotStartWith("[");

        // 저장 포맷이 바뀌어도 응답은 위치 배열 그대로다.
        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows[0].cells[0]").value("네트워크"))
                .andExpect(jsonPath("$.data.rows[0].cells[1]").value("92"));
    }

    @Test
    @DisplayName("PATCH row - 열 수보다 짧은 cells는 빈 값으로 채워진다")
    void update_row_pads_short_cells() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");

        mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", id, 0)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"운영체제\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cells", hasSize(2)))
                .andExpect(jsonPath("$.data.cells[1]").value(""));

        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows[0].cells", hasSize(2)))
                .andExpect(jsonPath("$.data.rows[0].cells[0]").value("운영체제"))
                .andExpect(jsonPath("$.data.rows[0].cells[1]").value(""));
    }

    @Test
    @DisplayName("PATCH row - 열 수를 넘는 cells는 담을 key가 없어 버려진다")
    void update_row_drops_excess_cells() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");

        mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", id, 0)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"운영체제\",\"78\",\"초과분\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cells", hasSize(2)));

        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows[0].cells", hasSize(2)))
                .andExpect(jsonPath("$.data.rows[0].cells[1]").value("78"));
    }

    @Test
    @DisplayName("열 이름을 바꿔도 key가 그대로라 셀 값이 유지된다")
    void rename_column_keeps_cell_values() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");

        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}", id, "c1")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"최종점수\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows[0].cells[1]").value("92"));
    }

    @Test
    @DisplayName("PATCH column - label이 변경되고 행 데이터는 그대로다")
    void rename_column() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");

        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}", id, "c1")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"최종점수\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns", hasSize(2)))
                .andExpect(jsonPath("$.data.columns[0].label").value("과목"))
                .andExpect(jsonPath("$.data.columns[1].key").value("c1"))
                .andExpect(jsonPath("$.data.columns[1].label").value("최종점수"));

        mockMvc.perform(get("/api/datasets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.columns[1].label").value("최종점수"));

        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows", hasSize(1)))
                .andExpect(jsonPath("$.data.rows[0].cells[0]").value("네트워크"))
                .andExpect(jsonPath("$.data.rows[0].cells[1]").value("92"));
    }

    @Test
    @DisplayName("PATCH column - 없는 key면 404")
    void rename_missing_column_404() throws Exception {
        long id = createDataset();
        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}", id, "c9")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH column - label이 비면 400")
    void rename_blank_label_400() throws Exception {
        long id = createDataset();
        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}", id, "c0")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("타인 dataset 접근 시 404")
    void other_member_404() throws Exception {
        long id = createDataset();
        String otherAuth = "Bearer " + AuthFixture.loginAndGetAccessToken(
                mockMvc, "other", "password");

        mockMvc.perform(get("/api/datasets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, otherAuth))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}", id, "c0")
                        .header(HttpHeaders.AUTHORIZATION, otherAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"해킹\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/datasets/{id}/rows/bulk", id)
                        .header(HttpHeaders.AUTHORIZATION, otherAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[[\"x\"]]}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/datasets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, otherAuth))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE dataset - 삭제 시 행도 cascade로 함께 삭제된다")
    void delete_dataset_cascades_rows() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"A\",\"1\"],[\"B\",\"2\"]]");

        mockMvc.perform(delete("/api/datasets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/datasets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound());
        org.assertj.core.api.Assertions
                .assertThat(datasetRowRepository.countByDatasetId(id))
                .isZero();
    }

    // ---------- 열 너비(resize) ----------

    /** 열 너비를 바꾸고 응답 본문을 돌려준다. */
    private String resize(long id, String key, String widthJson) throws Exception {
        return mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/width", id, key)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"width\":" + widthJson + "}"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("PATCH columns/{key}/width - 너비를 저장하고 조회 시 유지된다")
    void resize_column() throws Exception {
        long id = createDataset();

        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/width", id, "c1")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"width\":240}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[1].width").value(240));

        // 지정하지 않은 열은 width가 없다(기본 폭).
        mockMvc.perform(get("/api/datasets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[1].width").value(240))
                .andExpect(jsonPath("$.data.columns[0].width").doesNotExist());
    }

    @Test
    @DisplayName("PATCH columns/{key}/width - 범위 밖 너비는 거부한다")
    void resize_column_out_of_range() throws Exception {
        long id = createDataset();

        for (String bad : new String[]{"59", "801", "0", "-10"}) {
            mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/width", id, "c0")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"width\":" + bad + "}"))
                    .andExpect(status().isBadRequest());
        }
        // 경계값은 통과해야 한다.
        for (String ok : new String[]{"60", "800"}) {
            mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/width", id, "c0")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"width\":" + ok + "}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("PATCH columns/{key}/width - width 누락은 거부한다")
    void resize_column_requires_width() throws Exception {
        long id = createDataset();

        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/width", id, "c0")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH columns/{key}/width - 없는 열은 404")
    void resize_column_not_found() throws Exception {
        long id = createDataset();

        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/width", id, "c99")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"width\":200}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE columns/{key}/width - 너비를 지우면 기본 폭으로 돌아간다")
    void reset_column_width() throws Exception {
        long id = createDataset();
        resize(id, "c0", "300");

        mockMvc.perform(delete("/api/datasets/{id}/columns/{key}/width", id, "c0")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[0].width").doesNotExist());
    }

    @Test
    @DisplayName("이름을 바꿔도 열 너비는 유지된다")
    void rename_preserves_width() throws Exception {
        long id = createDataset();
        resize(id, "c0", "300");

        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}", id, "c0")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"바뀐이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[0].label").value("바뀐이름"))
                .andExpect(jsonPath("$.data.columns[0].width").value(300));
    }

    @Test
    @DisplayName("순서를 바꿔도 너비는 열을 따라간다")
    void reorder_carries_width() throws Exception {
        long id = createDataset();
        resize(id, "c0", "300");

        mockMvc.perform(patch("/api/datasets/{id}/columns/order", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[\"c1\",\"c0\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[0].key").value("c1"))
                .andExpect(jsonPath("$.data.columns[0].width").doesNotExist())
                .andExpect(jsonPath("$.data.columns[1].key").value("c0"))
                .andExpect(jsonPath("$.data.columns[1].width").value(300));
    }

    @Test
    @DisplayName("너비를 바꿔도 행 값은 그대로다 - 열 단위 표시 속성이라 행을 안 건드린다")
    void resize_does_not_touch_rows() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");

        resize(id, "c0", "300");

        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[0].cells[0]").value("네트워크"))
                .andExpect(jsonPath("$.data.rows[0].cells[1]").value("92"));
    }

    @Test
    @DisplayName("생성 - 범위 밖 width를 넣으면 거부한다 (resize 상·하한을 생성으로 우회 못 함)")
    void create_rejects_out_of_range_width() throws Exception {
        mockMvc.perform(post("/api/datasets")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columns":[{"key":"c0","label":"과목","width":9999}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("생성 - width를 함께 주면 저장된다")
    void create_with_width() throws Exception {
        mockMvc.perform(post("/api/datasets")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columns":[{"key":"c0","label":"과목","width":150}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.columns[0].width").value(150));
    }

    @Test
    @DisplayName("남의 데이터셋 열 너비는 못 바꾼다")
    void resize_column_of_other_member() throws Exception {
        long id = createDataset();
        String otherToken = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");

        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/width", id, "c0")
                        .header(HttpHeaders.AUTHORIZATION, otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"width\":200}"))
                .andExpect(status().isNotFound());
    }

    // ---------- 열 기본 정렬(align) ----------

    /** 열 기본 정렬을 바꾸고 응답 본문을 돌려준다. */
    private String setColumnAlign(long id, String key, String align) throws Exception {
        return mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/align", id, key)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"align\":\"" + align + "\"}"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("PATCH columns/{key}/align - 정렬을 저장하고 조회 시 유지된다")
    void set_column_align() throws Exception {
        long id = createDataset();

        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/align", id, "c1")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"align\":\"right\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[1].align").value("right"));

        // 지정하지 않은 열은 align이 없다(기본 정렬 left).
        mockMvc.perform(get("/api/datasets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[1].align").value("right"))
                .andExpect(jsonPath("$.data.columns[0].align").doesNotExist());
    }

    @Test
    @DisplayName("PATCH columns/{key}/align - 허용되지 않은 정렬은 거부한다")
    void set_column_align_invalid() throws Exception {
        long id = createDataset();

        for (String bad : new String[]{"top", "justify", "LEFT", ""}) {
            mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/align", id, "c0")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"align\":\"" + bad + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        // 허용값은 통과해야 한다.
        for (String ok : new String[]{"left", "center", "right"}) {
            mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/align", id, "c0")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"align\":\"" + ok + "\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("PATCH columns/{key}/align - align 누락은 거부한다")
    void set_column_align_requires_align() throws Exception {
        long id = createDataset();

        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/align", id, "c0")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH columns/{key}/align - 없는 열은 404")
    void set_column_align_not_found() throws Exception {
        long id = createDataset();

        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/align", id, "c99")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"align\":\"center\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE columns/{key}/align - 정렬을 지우면 기본 정렬로 돌아간다")
    void reset_column_align() throws Exception {
        long id = createDataset();
        setColumnAlign(id, "c0", "center");

        mockMvc.perform(delete("/api/datasets/{id}/columns/{key}/align", id, "c0")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[0].align").doesNotExist());
    }

    @Test
    @DisplayName("이름·너비를 바꿔도 열 정렬은 유지된다")
    void rename_and_resize_preserve_align() throws Exception {
        long id = createDataset();
        setColumnAlign(id, "c0", "center");

        // rename은 align 보존
        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}", id, "c0")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"바뀐이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[0].align").value("center"));

        // width 변경도 align 보존
        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/width", id, "c0")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"width\":300}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[0].align").value("center"))
                .andExpect(jsonPath("$.data.columns[0].width").value(300));
    }

    @Test
    @DisplayName("순서를 바꿔도 정렬은 열을 따라간다")
    void reorder_carries_align() throws Exception {
        long id = createDataset();
        setColumnAlign(id, "c0", "right");

        mockMvc.perform(patch("/api/datasets/{id}/columns/order", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[\"c1\",\"c0\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[0].key").value("c1"))
                .andExpect(jsonPath("$.data.columns[0].align").doesNotExist())
                .andExpect(jsonPath("$.data.columns[1].key").value("c0"))
                .andExpect(jsonPath("$.data.columns[1].align").value("right"));
    }

    @Test
    @DisplayName("생성 - 허용되지 않은 align을 넣으면 거부한다 (검증을 생성으로 우회 못 함)")
    void create_rejects_invalid_align() throws Exception {
        mockMvc.perform(post("/api/datasets")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columns":[{"key":"c0","label":"과목","align":"top"}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("생성 - align을 함께 주면 저장된다")
    void create_with_align() throws Exception {
        mockMvc.perform(post("/api/datasets")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columns":[{"key":"c0","label":"과목","align":"center"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.columns[0].align").value("center"));
    }

    @Test
    @DisplayName("남의 데이터셋 열 정렬은 못 바꾼다")
    void set_column_align_of_other_member() throws Exception {
        long id = createDataset();
        String otherToken = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");

        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/align", id, "c0")
                        .header(HttpHeaders.AUTHORIZATION, otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"align\":\"center\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------- 셀 서식(배경색·정렬) ----------

    /** 셀 서식을 지정하고 응답을 돌려준다. */
    private String setStyle(long id, int rowIndex, String colKey, String bodyJson) throws Exception {
        return mockMvc.perform(put("/api/datasets/{id}/rows/{r}/cells/{c}/style", id, rowIndex, colKey)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("PUT cells/{col}/style - 배경색·정렬을 저장하고 행 조회 시 styles로 온다")
    void set_cell_style() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");

        mockMvc.perform(put("/api/datasets/{id}/rows/0/cells/c1/style", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bg\":\"green\",\"align\":\"right\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.styles.c1.bg").value("green"))
                .andExpect(jsonPath("$.data.styles.c1.align").value("right"));

        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[0].styles.c1.bg").value("green"))
                .andExpect(jsonPath("$.data.rows[0].styles.c1.align").value("right"))
                // 서식 없는 셀은 styles에 아예 없다(sparse).
                .andExpect(jsonPath("$.data.rows[0].styles.c0").doesNotExist());
    }

    @Test
    @DisplayName("PUT cells/{col}/style - 빈 요청이면 서식을 지운다")
    void clear_cell_style() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");
        setStyle(id, 0, "c1", "{\"bg\":\"green\"}");

        mockMvc.perform(put("/api/datasets/{id}/rows/0/cells/c1/style", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.styles.c1").doesNotExist());

        // 빈 서식 행이 남지 않아야 한다(sparse 유지).
        org.assertj.core.api.Assertions
                .assertThat(datasetCellStyleRepository.findByRowIdAndColKey(rowIds(id).get(0), "c1"))
                .isEmpty();
    }

    @Test
    @DisplayName("PUT cells/{col}/style - 통째로 교체한다(부분 갱신 아님)")
    void set_cell_style_replaces() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");
        setStyle(id, 0, "c1", "{\"bg\":\"green\",\"align\":\"right\"}");

        // align만 보내면 bg는 사라진다.
        mockMvc.perform(put("/api/datasets/{id}/rows/0/cells/c1/style", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"align\":\"center\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.styles.c1.align").value("center"))
                .andExpect(jsonPath("$.data.styles.c1.bg").doesNotExist());
    }

    @Test
    @DisplayName("PUT cells/{col}/style - 허용되지 않은 색/정렬은 거부한다")
    void set_cell_style_rejects_invalid() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");

        mockMvc.perform(put("/api/datasets/{id}/rows/0/cells/c1/style", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bg\":\"#ff0000\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/datasets/{id}/rows/0/cells/c1/style", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"align\":\"justify\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT cells/{col}/style - 없는 열은 404")
    void set_cell_style_unknown_column() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");

        mockMvc.perform(put("/api/datasets/{id}/rows/0/cells/c99/style", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bg\":\"green\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("셀 서식을 바꿔도 값·수식은 그대로다 - 표시 속성이라 cells를 안 건드린다")
    void set_cell_style_keeps_value_and_formula() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"3\",\"4\"]]");
        // c1을 수식으로 만든다. 참조는 label(과목) 기반이고 열 이름은 중괄호로 감싼다.
        mockMvc.perform(patch("/api/datasets/{id}/rows/0", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"3\",\"={과목}*2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cells[1]").value("6"));

        setStyle(id, 0, "c1", "{\"bg\":\"yellow\"}");

        // 값(6)과 수식이 유지돼야 한다.
        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[0].cells[1]").value("6"))
                .andExpect(jsonPath("$.data.rows[0].formulas.c1").exists())
                .andExpect(jsonPath("$.data.rows[0].styles.c1.bg").value("yellow"));
    }

    @Test
    @DisplayName("열을 지우면 그 열의 셀 서식도 함께 정리된다")
    void delete_column_clears_styles() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");
        setStyle(id, 0, "c1", "{\"bg\":\"green\"}");
        long rowId = rowIds(id).get(0);
        org.assertj.core.api.Assertions
                .assertThat(datasetCellStyleRepository.findByRowIdAndColKey(rowId, "c1"))
                .isPresent();

        mockMvc.perform(delete("/api/datasets/{id}/columns/c1", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());

        // 지연 정리가 아니라 즉시 정리다(수식과 같은 방식).
        org.assertj.core.api.Assertions
                .assertThat(datasetCellStyleRepository.findByRowIdAndColKey(rowId, "c1"))
                .isEmpty();
    }

    @Test
    @DisplayName("행을 지우면 그 행의 셀 서식도 cascade로 사라진다")
    void delete_row_cascades_styles() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");
        setStyle(id, 0, "c1", "{\"bg\":\"green\"}");
        long rowId = rowIds(id).get(0);

        mockMvc.perform(delete("/api/datasets/{id}/rows/0", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions
                .assertThat(datasetCellStyleRepository.findByRowIdAndColKey(rowId, "c1"))
                .isEmpty();
    }

    @Test
    @DisplayName("남의 데이터셋 셀 서식은 못 바꾼다")
    void set_cell_style_of_other_member() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"네트워크\",\"92\"]]");
        String otherToken = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");

        mockMvc.perform(put("/api/datasets/{id}/rows/0/cells/c1/style", id)
                        .header(HttpHeaders.AUTHORIZATION, otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bg\":\"green\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------- 셀 서식 일괄(선택 범위·행·열·표 전체) ----------

    @Test
    @DisplayName("PUT cells/style(bulk) - 여러 셀 서식을 한 번에 지정하고 영향 행을 돌려준다")
    void set_cell_styles_bulk() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"a\",\"1\"],[\"b\",\"2\"]]");

        mockMvc.perform(put("/api/datasets/{id}/cells/style", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":["
                                + "{\"rowIndex\":0,\"colKey\":\"c1\",\"bg\":\"green\"},"
                                + "{\"rowIndex\":1,\"colKey\":\"c1\",\"bg\":\"green\",\"align\":\"center\"}]}"))
                .andExpect(status().isOk())
                // 영향받은 행이 rowIndex 오름차순으로 온다.
                .andExpect(jsonPath("$.data[0].rowIndex").value(0))
                .andExpect(jsonPath("$.data[0].styles.c1.bg").value("green"))
                .andExpect(jsonPath("$.data[1].rowIndex").value(1))
                .andExpect(jsonPath("$.data[1].styles.c1.bg").value("green"))
                .andExpect(jsonPath("$.data[1].styles.c1.align").value("center"));

        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.rows[0].styles.c1.bg").value("green"))
                .andExpect(jsonPath("$.data.rows[1].styles.c1.align").value("center"))
                // 서식 없는 셀은 sparse.
                .andExpect(jsonPath("$.data.rows[0].styles.c0").doesNotExist());
    }

    @Test
    @DisplayName("PUT cells/style(bulk) - bg·align 모두 빈 대상은 그 셀 서식을 지운다")
    void bulk_style_clears_empty_target() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"a\",\"1\"]]");
        setStyle(id, 0, "c1", "{\"bg\":\"green\"}");

        mockMvc.perform(put("/api/datasets/{id}/cells/style", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[{\"rowIndex\":0,\"colKey\":\"c1\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].styles.c1").doesNotExist());

        org.assertj.core.api.Assertions
                .assertThat(datasetCellStyleRepository.findByRowIdAndColKey(rowIds(id).get(0), "c1"))
                .isEmpty();
    }

    @Test
    @DisplayName("PUT cells/style(bulk) - 허용되지 않은 색은 거부한다")
    void bulk_style_rejects_invalid() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"a\",\"1\"]]");

        mockMvc.perform(put("/api/datasets/{id}/cells/style", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[{\"rowIndex\":0,\"colKey\":\"c1\",\"bg\":\"pink\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT cells/style(bulk) - 대상이 비면 400")
    void bulk_style_rejects_empty() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"a\",\"1\"]]");

        mockMvc.perform(put("/api/datasets/{id}/cells/style", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT cells/style(bulk) - 없는 열이 섞이면 404")
    void bulk_style_unknown_column() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"a\",\"1\"]]");

        mockMvc.perform(put("/api/datasets/{id}/cells/style", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[{\"rowIndex\":0,\"colKey\":\"c99\",\"bg\":\"green\"}]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT cells/style(bulk) - 남의 데이터셋은 못 바꾼다")
    void bulk_style_of_other_member() throws Exception {
        long id = createDataset();
        bulk(id, "[[\"a\",\"1\"]]");
        String otherToken = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");

        mockMvc.perform(put("/api/datasets/{id}/cells/style", id)
                        .header(HttpHeaders.AUTHORIZATION, otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[{\"rowIndex\":0,\"colKey\":\"c1\",\"bg\":\"green\"}]}"))
                .andExpect(status().isNotFound());
    }

    // ---------- 셀 병합(가로·세로) ----------

    /** 3열 dataset(c0/c1/c2)을 만들고 id를 반환한다 — colspan 검증엔 열이 3개 필요하다. */
    private long createDataset3() throws Exception {
        String body = mockMvc.perform(post("/api/datasets")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columns":[
                                  {"key":"c0","label":"과목"},
                                  {"key":"c1","label":"점수"},
                                  {"key":"c2","label":"비고"}
                                ]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 앵커 셀을 병합하고 응답을 돌려준다. */
    private String merge(long id, int rowIndex, String colKey, int rowSpan, int colSpan)
            throws Exception {
        return mockMvc.perform(put("/api/datasets/{id}/rows/{r}/cells/{c}/merge", id, rowIndex, colKey)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowSpan\":" + rowSpan + ",\"colSpan\":" + colSpan + "}"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("PUT cells/{col}/merge - 병합을 저장하고 갱신된 병합 전체를 돌려준다")
    void set_cell_merge() throws Exception {
        long id = createDataset3();
        bulk(id, "[[\"네트워크\",\"92\",\"재수강\"]]");

        mockMvc.perform(put("/api/datasets/{id}/rows/0/cells/c0/merge", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowSpan\":1,\"colSpan\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merges", hasSize(1)))
                .andExpect(jsonPath("$.data.merges[0].rowIndex").value(0))
                .andExpect(jsonPath("$.data.merges[0].colKey").value("c0"))
                .andExpect(jsonPath("$.data.merges[0].rowSpan").value(1))
                .andExpect(jsonPath("$.data.merges[0].colSpan").value(2));

        // GET /merges로도 그 dataset의 병합 전체가 온다.
        mockMvc.perform(get("/api/datasets/{id}/merges", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merges", hasSize(1)))
                .andExpect(jsonPath("$.data.merges[0].colKey").value("c0"))
                .andExpect(jsonPath("$.data.merges[0].colSpan").value(2));
    }

    @Test
    @DisplayName("세로 병합(rowSpan>1)도 저장된다 — 앵커 행 번호로 온다")
    void set_vertical_merge() throws Exception {
        long id = createDataset3();
        bulk(id, "[[\"a\",\"b\",\"c\"],[\"d\",\"e\",\"f\"],[\"g\",\"h\",\"i\"]]");

        // 1행(rowIndex 1)의 c0에서 아래로 2칸 병합.
        mockMvc.perform(put("/api/datasets/{id}/rows/1/cells/c0/merge", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowSpan\":2,\"colSpan\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merges", hasSize(1)))
                .andExpect(jsonPath("$.data.merges[0].rowIndex").value(1))
                .andExpect(jsonPath("$.data.merges[0].colKey").value("c0"))
                .andExpect(jsonPath("$.data.merges[0].rowSpan").value(2))
                .andExpect(jsonPath("$.data.merges[0].colSpan").value(1));
    }

    @Test
    @DisplayName("병합 - 아래 경계를 넘으면 400")
    void merge_below_bottom_edge() throws Exception {
        long id = createDataset3();
        bulk(id, "[[\"a\",\"b\",\"c\"],[\"d\",\"e\",\"f\"]]"); // 2행

        // 1행에서 rowSpan=2면 1..2인데 2행이 없다(행은 0,1뿐).
        mockMvc.perform(put("/api/datasets/{id}/rows/1/cells/c0/merge", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowSpan\":2,\"colSpan\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("병합 - 세로 병합끼리 직사각형이 겹치면 400")
    void merge_vertical_overlap_rejected() throws Exception {
        long id = createDataset3();
        bulk(id, "[[\"a\",\"b\",\"c\"],[\"d\",\"e\",\"f\"],[\"g\",\"h\",\"i\"]]");
        merge(id, 0, "c0", 2, 1); // 0..1행 c0

        // 1행 c0에서 병합 시도 → 위 병합의 직사각형과 겹친다.
        mockMvc.perform(put("/api/datasets/{id}/rows/1/cells/c0/merge", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowSpan\":2,\"colSpan\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("병합해도 덮인 셀의 값은 cells에 보존된다(오버레이)")
    void merge_preserves_covered_values() throws Exception {
        long id = createDataset3();
        bulk(id, "[[\"네트워크\",\"92\",\"재수강\"]]");

        merge(id, 0, "c0", 1, 2); // c0가 c0..c1을 덮는다

        // 값은 직사각형 그대로 — 덮인 c1(92)도 남아 있다.
        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[0].cells[0]").value("네트워크"))
                .andExpect(jsonPath("$.data.rows[0].cells[1]").value("92"))
                .andExpect(jsonPath("$.data.rows[0].cells[2]").value("재수강"));
    }

    @Test
    @DisplayName("DELETE merge - 분할하면 merges가 사라진다(값은 원래대로)")
    void unmerge_cell() throws Exception {
        long id = createDataset3();
        bulk(id, "[[\"네트워크\",\"92\",\"재수강\"]]");
        merge(id, 0, "c0", 1, 2);

        mockMvc.perform(delete("/api/datasets/{id}/rows/0/cells/c0/merge", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merges", hasSize(0)));

        // 병합은 비고 값은 그대로.
        mockMvc.perform(get("/api/datasets/{id}/merges", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merges", hasSize(0)));
        mockMvc.perform(get("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[0].cells[1]").value("92"));
    }

    @Test
    @DisplayName("병합 - 오른쪽 경계를 넘으면 400")
    void merge_out_of_bounds() throws Exception {
        long id = createDataset3(); // c0/c1/c2
        bulk(id, "[[\"a\",\"b\",\"c\"]]");

        // c1에서 colSpan=3이면 c1..c3인데 c3가 없다.
        mockMvc.perform(put("/api/datasets/{id}/rows/0/cells/c1/merge", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowSpan\":1,\"colSpan\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("병합 - (1,1)은 병합이 아니라 400")
    void merge_rejects_noop() throws Exception {
        long id = createDataset3();
        bulk(id, "[[\"a\",\"b\",\"c\"]]");

        mockMvc.perform(put("/api/datasets/{id}/rows/0/cells/c0/merge", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowSpan\":1,\"colSpan\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("병합 - 다른 병합과 겹치면 400")
    void merge_overlap_rejected() throws Exception {
        long id = createDataset3();
        bulk(id, "[[\"a\",\"b\",\"c\"]]");
        merge(id, 0, "c0", 1, 2); // c0..c1

        // c1에서 병합 시도 → c0의 영역과 겹친다.
        mockMvc.perform(put("/api/datasets/{id}/rows/0/cells/c1/merge", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowSpan\":1,\"colSpan\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("병합 - 없는 열은 404")
    void merge_column_not_found() throws Exception {
        long id = createDataset3();
        bulk(id, "[[\"a\",\"b\",\"c\"]]");

        mockMvc.perform(put("/api/datasets/{id}/rows/0/cells/c99/merge", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowSpan\":1,\"colSpan\":2}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("병합에 걸친 열을 삭제하면 병합이 해제된다")
    void delete_column_dissolves_merge() throws Exception {
        long id = createDataset3();
        bulk(id, "[[\"a\",\"b\",\"c\"]]");
        merge(id, 0, "c0", 1, 2); // c0가 c0..c1을 덮는다

        // 덮인 열 c1을 삭제 → 병합 해제.
        mockMvc.perform(delete("/api/datasets/{id}/columns/{key}", id, "c1")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/datasets/{id}/merges", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merges", hasSize(0)));
    }

    @Test
    @DisplayName("열 순서를 바꾸면 병합이 해제된다(v1 보수적 처리)")
    void reorder_dissolves_merge() throws Exception {
        long id = createDataset3();
        bulk(id, "[[\"a\",\"b\",\"c\"]]");
        merge(id, 0, "c0", 1, 2);

        mockMvc.perform(patch("/api/datasets/{id}/columns/order", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[\"c2\",\"c1\",\"c0\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/datasets/{id}/merges", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merges", hasSize(0)));
    }

    @Test
    @DisplayName("가로 병합 안에 열을 삽입하면 그 병합이 해제된다(#829 O3)")
    void insert_column_inside_merge_dissolves_it() throws Exception {
        long id = createDataset3(); // c0/c1/c2
        bulk(id, "[[\"a\",\"b\",\"c\"]]");
        merge(id, 0, "c0", 1, 2); // c0..c1

        // c0와 c1 사이(index 1)에 열 삽입 → 그 병합은 온전할 수 없어 해제.
        mockMvc.perform(post("/api/datasets/{id}/columns", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"atIndex\":1}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/datasets/{id}/merges", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merges", hasSize(0)));
    }

    @Test
    @DisplayName("가로 병합 밖(앞)에 열을 삽입하면 병합은 유지된다")
    void insert_column_outside_merge_keeps_it() throws Exception {
        long id = createDataset3();
        bulk(id, "[[\"a\",\"b\",\"c\"]]");
        merge(id, 0, "c1", 1, 2); // c1..c2

        // 맨 앞(index 0)에 삽입 → c1..c2 병합은 함께 밀리고 온전.
        mockMvc.perform(post("/api/datasets/{id}/columns", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"atIndex\":0}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/datasets/{id}/merges", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merges", hasSize(1)))
                .andExpect(jsonPath("$.data.merges[0].colKey").value("c1"));
    }

    @Test
    @DisplayName("세로 병합 안에 행을 삽입하면 그 병합이 해제된다(#829 O3)")
    void insert_row_inside_vertical_merge_dissolves_it() throws Exception {
        long id = createDataset3();
        bulk(id, "[[\"a\",\"b\",\"c\"],[\"d\",\"e\",\"f\"],[\"g\",\"h\",\"i\"]]");
        merge(id, 0, "c0", 2, 1); // 0..1행 세로 병합

        // 0행과 1행 사이(index 1)에 행 삽입 → 세로 병합 해제.
        mockMvc.perform(post("/api/datasets/{id}/rows", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"atIndex\":1,\"cells\":[\"x\",\"y\",\"z\"]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/datasets/{id}/merges", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merges", hasSize(0)));
    }

    @Test
    @DisplayName("앵커 행을 삭제하면 병합도 cascade로 사라진다")
    void delete_row_cascades_merge() throws Exception {
        long id = createDataset3();
        bulk(id, "[[\"a\",\"b\",\"c\"],[\"d\",\"e\",\"f\"]]");
        merge(id, 0, "c0", 1, 2);

        mockMvc.perform(delete("/api/datasets/{id}/rows/0", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions
                .assertThat(datasetMergeRepository.findByDatasetId(id))
                .isEmpty();
    }

    @Test
    @DisplayName("남의 데이터셋 셀은 못 병합한다")
    void merge_of_other_member() throws Exception {
        long id = createDataset3();
        bulk(id, "[[\"a\",\"b\",\"c\"]]");
        String otherToken = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");

        mockMvc.perform(put("/api/datasets/{id}/rows/0/cells/c0/merge", id)
                        .header(HttpHeaders.AUTHORIZATION, otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowSpan\":1,\"colSpan\":2}"))
                .andExpect(status().isNotFound());
    }
}
