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

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 열 허용값 목록(enum, R3 #914). 느슨 — 목록만 정규화해 저장하고 값은 강제하지 않는다. */
class DatasetColumnOptionsTest extends ApiTestSupport {

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
                        .content("{\"columns\":[{\"key\":\"c0\",\"label\":\"통화\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        datasetId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private ResultActions setOptions(String bodyJson) throws Exception {
        return mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/options", datasetId, "c0")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyJson));
    }

    private ResultActions meta() throws Exception {
        return mockMvc.perform(get("/api/datasets/{id}", datasetId)
                .header(HttpHeaders.AUTHORIZATION, authHeader));
    }

    @Test
    @DisplayName("허용값 목록을 설정하면 열에 담기고 조회에 실린다")
    void setOptionsPersists() throws Exception {
        setOptions("{\"options\":[\"원\",\"엔\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[0].options", contains("원", "엔")));
        meta().andExpect(jsonPath("$.data.columns[0].options", contains("원", "엔")));
    }

    @Test
    @DisplayName("공백 정리·중복 제거·순서 보존")
    void normalizes() throws Exception {
        setOptions("{\"options\":[\"원\",\"  원  \",\"엔\",\"\",\"엔\"]}")
                .andExpect(jsonPath("$.data.columns[0].options", contains("원", "엔")));
    }

    @Test
    @DisplayName("빈 목록이면 해제된다")
    void emptyClears() throws Exception {
        setOptions("{\"options\":[\"원\"]}").andExpect(status().isOk());
        setOptions("{\"options\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[0].options").doesNotExist());
    }

    @Test
    @DisplayName("null이면 해제된다")
    void nullClears() throws Exception {
        setOptions("{\"options\":[\"원\"]}").andExpect(status().isOk());
        setOptions("{\"options\":null}")
                .andExpect(jsonPath("$.data.columns[0].options").doesNotExist());
    }

    @Test
    @DisplayName("없는 열은 404")
    void missingColumn() throws Exception {
        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/options", datasetId, "c9")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"options\":[\"원\"]}"))
                .andExpect(status().isNotFound());
    }
}
