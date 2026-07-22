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

/**
 * 표간 절대셀 참조(R9 #918). A표({@code 도시})의 수식이 B표({@code 요약})의 셀을 읽어 계산한다.
 * 반응성(표간 전파)은 이 슬라이스 밖(#915b) — 저장 시 정확 계산까지.
 */
class DatasetCrossRefTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private long cityId;    // A: 금액(c0)·원화(c1)
    private long summaryId; // B: 환율(c0), 이름 "요약", 1행 = 1300

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);

        cityId = create("[{\"key\":\"c0\",\"label\":\"금액\"},{\"key\":\"c1\",\"label\":\"원화\"}]");
        mockMvc.perform(post("/api/datasets/{id}/rows/bulk", cityId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[[\"100\",\"\"]]}"))
                .andExpect(status().isOk());

        summaryId = create("[{\"key\":\"c0\",\"label\":\"환율\"}]");
        mockMvc.perform(patch("/api/datasets/{id}/name", summaryId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"요약\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/datasets/{id}/rows/bulk", summaryId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[[\"1300\"]]}"))
                .andExpect(status().isOk());
    }

    private long create(String columnsJson) throws Exception {
        String body = mockMvc.perform(post("/api/datasets")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"columns\":" + columnsJson + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** cityId의 한 행을 tableRefs와 함께 수정. */
    private ResultActions patchCity(String cells, String tableRefs) throws Exception {
        return mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", cityId, 0)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cells\":" + cells + ",\"tableRefs\":" + tableRefs + "}"));
    }

    private ResultActions cityRows() throws Exception {
        return mockMvc.perform(get("/api/datasets/{id}/rows", cityId)
                .header(HttpHeaders.AUTHORIZATION, authHeader));
    }

    @Test
    @DisplayName("A표 수식이 B표 셀을 읽어 계산한다 — 핵심")
    void crossTableCellIsRead() throws Exception {
        // 원화 = 금액 * 요약!환율(1300) = 100 * 1300 = 130000.
        patchCity("[\"100\",\"={금액} * {요약!환율}1\"]", "{\"요약\":" + summaryId + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.edited.cells[1]").value("130000"));

        // 표시형은 표 이름·열 label·행 번호로 되돌아온다.
        cityRows().andExpect(jsonPath("$.data.rows[0].cells[1]").value("130000"))
                .andExpect(jsonPath("$.data.rows[0].formulas.c1")
                        .value("=({금액} * {요약!환율}1)"));
    }

    @Test
    @DisplayName("순수 표간 셀 참조")
    void pureCrossCell() throws Exception {
        patchCity("[\"100\",\"={요약!환율}1\"]", "{\"요약\":" + summaryId + "}")
                .andExpect(jsonPath("$.data.edited.cells[1]").value("1300"));
    }

    @Test
    @DisplayName("tableRefs에 없는 표 이름은 400")
    void unknownTableNameRejected() throws Exception {
        patchCity("[\"100\",\"={없는표!환율}1\"]", "{\"요약\":" + summaryId + "}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("내 것이 아닌 표(없는 id)는 400 — 남의 표 참조 차단")
    void notOwnedTableRejected() throws Exception {
        patchCity("[\"100\",\"={요약!환율}1\"]", "{\"요약\":999999}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("대상 표에 없는 행이면 400")
    void missingTargetRowRejected() throws Exception {
        // 요약 표는 1행뿐인데 2행을 가리킨다.
        patchCity("[\"100\",\"={요약!환율}2\"]", "{\"요약\":" + summaryId + "}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("표간 자동 재계산은 이 슬라이스 밖 — 대상이 바뀌어도 재저장 전엔 옛 값(#915b)")
    void noCrossPropagationYet() throws Exception {
        patchCity("[\"100\",\"={요약!환율}1\"]", "{\"요약\":" + summaryId + "}")
                .andExpect(jsonPath("$.data.edited.cells[1]").value("1300"));

        // 요약 표의 환율을 1300 → 1400으로 바꾼다.
        mockMvc.perform(patch("/api/datasets/{id}/rows/{i}", summaryId, 0)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cells\":[\"1400\"]}"))
                .andExpect(status().isOk());

        // 표간 전파가 없으니 도시 표는 아직 1300(재저장하면 1400). #915b에서 자동 갱신된다.
        cityRows().andExpect(jsonPath("$.data.rows[0].cells[1]").value("1300"));
    }
}
