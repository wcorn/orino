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

/** 열 숫자 서식(R2 #913). 표시 전용 토큰만 저장·검증한다(값·수식 무관). */
class DatasetColumnFormatTest extends ApiTestSupport {

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

    private ResultActions setFormat(String bodyJson) throws Exception {
        return mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/format", datasetId, "c0")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyJson));
    }

    private ResultActions meta() throws Exception {
        return mockMvc.perform(get("/api/datasets/{id}", datasetId)
                .header(HttpHeaders.AUTHORIZATION, authHeader));
    }

    @Test
    @DisplayName("서식을 설정하면 열에 담기고 조회에 실린다")
    void setFormatPersists() throws Exception {
        setFormat("{\"format\":\"KRW\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[0].format").value("KRW"));
        meta().andExpect(jsonPath("$.data.columns[0].format").value("KRW"));
    }

    @Test
    @DisplayName("다시 설정하면 교체된다")
    void replaceIsIdempotent() throws Exception {
        setFormat("{\"format\":\"KRW\"}").andExpect(status().isOk());
        setFormat("{\"format\":\"DECIMAL1\"}")
                .andExpect(jsonPath("$.data.columns[0].format").value("DECIMAL1"));
    }

    @Test
    @DisplayName("null이면 서식이 해제된다")
    void nullClearsFormat() throws Exception {
        setFormat("{\"format\":\"KRW\"}").andExpect(status().isOk());
        setFormat("{\"format\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[0].format").doesNotExist());
    }

    @Test
    @DisplayName("허용되지 않은 서식은 400")
    void invalidFormatRejected() throws Exception {
        setFormat("{\"format\":\"EUR\"}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("없는 열은 404")
    void missingColumnRejected() throws Exception {
        mockMvc.perform(patch("/api/datasets/{id}/columns/{key}/format", datasetId, "c9")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"KRW\"}"))
                .andExpect(status().isNotFound());
    }
}
