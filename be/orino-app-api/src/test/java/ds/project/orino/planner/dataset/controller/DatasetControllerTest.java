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
    @DisplayName("타인 dataset 접근 시 404")
    void other_member_404() throws Exception {
        long id = createDataset();
        String otherAuth = "Bearer " + AuthFixture.loginAndGetAccessToken(
                mockMvc, "other", "password");

        mockMvc.perform(get("/api/datasets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, otherAuth))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/datasets/{id}/rows/bulk", id)
                        .header(HttpHeaders.AUTHORIZATION, otherAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[[\"x\"]]}"))
                .andExpect(status().isNotFound());
    }
}
