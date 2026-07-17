package ds.project.orino.planner.dataset.controller;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DatasetControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DatasetRowRepository datasetRowRepository;
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
        mockMvc.perform(post("/api/datasets/{id}/columns", id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"  \"}"))
                .andExpect(status().isBadRequest());

        String otherAuth = "Bearer " + AuthFixture.loginAndGetAccessToken(
                mockMvc, "other", "password");
        mockMvc.perform(post("/api/datasets/{id}/columns", id)
                        .header(HttpHeaders.AUTHORIZATION, otherAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"침입\"}"))
                .andExpect(status().isNotFound());
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
}
