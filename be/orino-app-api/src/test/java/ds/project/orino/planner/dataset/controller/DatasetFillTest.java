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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 채우기 핸들(세로 드래그 범위 채우기). 소스 블록을 대상 행들에 타일링해 채운다. */
class DatasetFillTest extends ApiTestSupport {

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

    private ResultActions fill(String body) throws Exception {
        return mockMvc.perform(post("/api/datasets/{id}/cells/fill", datasetId)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions rows() throws Exception {
        return mockMvc.perform(get("/api/datasets/{id}/rows", datasetId)
                .header(HttpHeaders.AUTHORIZATION, authHeader));
    }

    @Test
    @DisplayName("수식을 아래로 채우면 행마다 자기 행 기준으로 상대 평가된다 — 핵심")
    void fillFormulaDownIsRelativePerRow() throws Exception {
        patchRow(0, "[\"10\",\"3\",\"={단가} * {수량}\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("30"));

        // c2 수식을 1~2행에 채운다.
        fill("{\"cols\":[\"c2\"],\"srcR0\":0,\"srcR1\":0,\"dstR0\":1,\"dstR1\":2}")
                .andExpect(status().isOk())
                // 응답은 영향 행(대상 1·2행)을 행 번호 순으로 싣는다.
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].rowIndex").value(1))
                .andExpect(jsonPath("$.data[0].cells[2]").value("40")) // 20*2
                .andExpect(jsonPath("$.data[1].rowIndex").value(2))
                .andExpect(jsonPath("$.data[1].cells[2]").value("20")); // 5*4

        // 원본 수식도 각 행에 심겼다(표시형은 열 이름 그대로).
        rows().andExpect(jsonPath("$.data.rows[1].cells[2]").value("40"))
                .andExpect(jsonPath("$.data.rows[1].formulas.c2").value("=({단가} * {수량})"))
                .andExpect(jsonPath("$.data.rows[2].cells[2]").value("20"));
    }

    @Test
    @DisplayName("리터럴을 아래로 채우면 값이 복사된다")
    void fillLiteralDown() throws Exception {
        fill("{\"cols\":[\"c0\"],\"srcR0\":0,\"srcR1\":0,\"dstR0\":1,\"dstR1\":2}")
                .andExpect(status().isOk());

        rows().andExpect(jsonPath("$.data.rows[1].cells[0]").value("10"))
                .andExpect(jsonPath("$.data.rows[2].cells[0]").value("10"));
    }

    @Test
    @DisplayName("리터럴로 채우면 대상의 기존 수식은 지워진다")
    void fillLiteralClearsExistingFormula() throws Exception {
        // 2행 c2를 수식으로 만든 뒤, 리터럴을 그 위로 채우면 수식이 사라진다.
        patchRow(2, "[\"5\",\"4\",\"={단가} * {수량}\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("20"));

        // 0행 c2(리터럴 "")를 1~2행에 채운다 → 2행 수식이 리터럴로 덮인다.
        fill("{\"cols\":[\"c2\"],\"srcR0\":0,\"srcR1\":0,\"dstR0\":1,\"dstR1\":2}")
                .andExpect(status().isOk());

        rows().andExpect(jsonPath("$.data.rows[2].cells[2]").value(""))
                .andExpect(jsonPath("$.data.rows[2].formulas.c2").doesNotExist());
    }

    @Test
    @DisplayName("위로도 채울 수 있다")
    void fillUp() throws Exception {
        patchRow(2, "[\"5\",\"4\",\"={단가} * {수량}\"]").andExpect(status().isOk());

        // 2행 수식을 0~1행에 위로 채운다.
        fill("{\"cols\":[\"c2\"],\"srcR0\":2,\"srcR1\":2,\"dstR0\":0,\"dstR1\":1}")
                .andExpect(status().isOk());

        rows().andExpect(jsonPath("$.data.rows[0].cells[2]").value("30")) // 10*3
                .andExpect(jsonPath("$.data.rows[1].cells[2]").value("40")); // 20*2
    }

    @Test
    @DisplayName("집계는 채운 뒤 최종 값으로 다시 계산된다(교차 행 반영)")
    void fillPropagatesToAggregateOnce() throws Exception {
        // 2행 c2에 c0 열 합계를 둔다(=SUM(단가) = 10+20+5 = 35).
        patchRow(2, "[\"5\",\"4\",\"=SUM({단가})\"]")
                .andExpect(jsonPath("$.data.edited.cells[2]").value("35"));

        // 0행 단가(10)를 1행에 채운다 → 1행 단가가 20→10. 집계는 10+10+5 = 25.
        fill("{\"cols\":[\"c0\"],\"srcR0\":0,\"srcR1\":0,\"dstR0\":1,\"dstR1\":1}")
                .andExpect(status().isOk());

        rows().andExpect(jsonPath("$.data.rows[1].cells[0]").value("10"))
                .andExpect(jsonPath("$.data.rows[2].cells[2]").value("25"));
    }

    @Test
    @DisplayName("대상이 소스와 인접하지 않으면 400")
    void nonAdjacentTargetRejected() throws Exception {
        // 소스 0행, 대상 2행(1행을 건너뜀) → 400.
        fill("{\"cols\":[\"c0\"],\"srcR0\":0,\"srcR1\":0,\"dstR0\":2,\"dstR1\":2}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("cols가 비면 400")
    void emptyColsRejected() throws Exception {
        fill("{\"cols\":[],\"srcR0\":0,\"srcR1\":0,\"dstR0\":1,\"dstR1\":1}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("표 밖 행이면 400")
    void outOfRangeRejected() throws Exception {
        fill("{\"cols\":[\"c0\"],\"srcR0\":2,\"srcR1\":2,\"dstR0\":3,\"dstR1\":3}")
                .andExpect(status().isBadRequest());
    }
}
