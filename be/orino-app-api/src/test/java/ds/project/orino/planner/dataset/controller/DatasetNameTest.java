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

/** 표 이름 설정/해제(#916). 표간 참조(#915)의 기반 — 이 페이즈는 이름 저장·조회만. */
class DatasetNameTest extends ApiTestSupport {

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
                        .content("{\"columns\":[{\"key\":\"c0\",\"label\":\"금액\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        datasetId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private ResultActions setName(String bodyJson) throws Exception {
        return mockMvc.perform(patch("/api/datasets/{id}/name", datasetId)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyJson));
    }

    private ResultActions meta() throws Exception {
        return mockMvc.perform(get("/api/datasets/{id}", datasetId)
                .header(HttpHeaders.AUTHORIZATION, authHeader));
    }

    @Test
    @DisplayName("표는 기본적으로 무명이다")
    void unnamedByDefault() throws Exception {
        meta().andExpect(jsonPath("$.data.name").doesNotExist());
    }

    @Test
    @DisplayName("이름을 설정하면 저장되고 조회에 실린다")
    void setNamePersists() throws Exception {
        setName("{\"name\":\"도쿄\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("도쿄"));
        meta().andExpect(jsonPath("$.data.name").value("도쿄"));
    }

    @Test
    @DisplayName("다시 설정하면 교체된다")
    void renameReplaces() throws Exception {
        setName("{\"name\":\"도쿄\"}").andExpect(status().isOk());
        setName("{\"name\":\"오사카\"}")
                .andExpect(jsonPath("$.data.name").value("오사카"));
    }

    @Test
    @DisplayName("빈 값이면 무명으로 되돌린다")
    void blankClearsName() throws Exception {
        setName("{\"name\":\"도쿄\"}").andExpect(status().isOk());
        setName("{\"name\":\"  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").doesNotExist());
    }

    @Test
    @DisplayName("255자를 넘으면 400")
    void tooLongRejected() throws Exception {
        String longName = "가".repeat(256);
        setName("{\"name\":\"" + longName + "\"}").andExpect(status().isBadRequest());
    }
}
