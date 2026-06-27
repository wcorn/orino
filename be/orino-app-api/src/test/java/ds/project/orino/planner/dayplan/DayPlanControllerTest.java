package ds.project.orino.planner.dayplan;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.dayplan.repository.DayPlanBlockRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DayPlanControllerTest extends ApiTestSupport {

    /** 월(1) 2블록 + 토(6) 1블록. 일부러 시작시각 역순으로 보내 정렬을 검증한다. */
    private static final String WEEKLY_BLOCKS = """
            { "blocks": [
              { "dayOfWeek": 1, "startTime": "10:00", "endTime": "12:00", "label": "개인 프로젝트", "color": "sky" },
              { "dayOfWeek": 1, "startTime": "08:00", "endTime": "09:00", "label": "기상",        "color": "violet" },
              { "dayOfWeek": 6, "startTime": "10:00", "endTime": "11:00", "label": "운동",        "color": "emerald" }
            ] }""";

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DayPlanBlockRepository blockRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private Long memberId;
    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberId = memberRepository.save(MemberFixture.create()).getId();
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    @Test
    @DisplayName("GET - 블록이 없으면 빈 목록을 반환한다")
    void get_empty() throws Exception {
        mockMvc.perform(get("/api/planner/plan").header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocks", hasSize(0)));
    }

    @Test
    @DisplayName("PUT - 전량 교체로 저장하고 요일·시작시각 순으로 반환한다")
    void put_replace() throws Exception {
        mockMvc.perform(putBlocks(WEEKLY_BLOCKS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocks", hasSize(3)))
                // 월(08:00) → 월(10:00) → 토(10:00)
                .andExpect(jsonPath("$.data.blocks[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.data.blocks[0].startTime").value("08:00"))
                .andExpect(jsonPath("$.data.blocks[0].label").value("기상"))
                .andExpect(jsonPath("$.data.blocks[0].id").exists())
                .andExpect(jsonPath("$.data.blocks[1].startTime").value("10:00"))
                .andExpect(jsonPath("$.data.blocks[1].label").value("개인 프로젝트"))
                .andExpect(jsonPath("$.data.blocks[2].dayOfWeek").value(6))
                .andExpect(jsonPath("$.data.blocks[2].color").value("emerald"));

        assertThat(blockRepository.findAllByMemberId(memberId)).hasSize(3);
    }

    @Test
    @DisplayName("PUT - 두 번째 저장은 기존 블록을 전량 교체한다")
    void put_replacesPrevious() throws Exception {
        mockMvc.perform(putBlocks(WEEKLY_BLOCKS)).andExpect(status().isOk());

        String fewer = """
                { "blocks": [
                  { "dayOfWeek": 3, "startTime": "09:00", "endTime": "10:00", "label": "회의", "color": null }
                ] }""";
        mockMvc.perform(putBlocks(fewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocks", hasSize(1)))
                .andExpect(jsonPath("$.data.blocks[0].dayOfWeek").value(3))
                .andExpect(jsonPath("$.data.blocks[0].label").value("회의"));

        assertThat(blockRepository.findAllByMemberId(memberId)).hasSize(1);
    }

    @Test
    @DisplayName("PUT - 빈 blocks는 전체 삭제한다")
    void put_empty_clears() throws Exception {
        mockMvc.perform(putBlocks(WEEKLY_BLOCKS)).andExpect(status().isOk());

        mockMvc.perform(putBlocks("{ \"blocks\": [] }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocks", hasSize(0)));

        assertThat(blockRepository.findAllByMemberId(memberId)).isEmpty();
    }

    @Test
    @DisplayName("PUT - 시간 역전(end<=start)이면 400 PLN-ERR-002")
    void put_timeReversed_400() throws Exception {
        String invalid = """
                { "blocks": [ { "dayOfWeek": 1, "startTime": "12:00", "endTime": "10:00", "label": "역전" } ] }""";
        mockMvc.perform(putBlocks(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLN-ERR-002"));
    }

    @Test
    @DisplayName("PUT - dayOfWeek가 0~6 밖이면 400 PLN-ERR-002")
    void put_dayOfWeekOutOfRange_400() throws Exception {
        String invalid = """
                { "blocks": [ { "dayOfWeek": 7, "startTime": "08:00", "endTime": "09:00", "label": "잘못된요일" } ] }""";
        mockMvc.perform(putBlocks(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLN-ERR-002"));
    }

    @Test
    @DisplayName("PUT - label이 공백이면 400 PLN-ERR-002")
    void put_blankLabel_400() throws Exception {
        String invalid = """
                { "blocks": [ { "dayOfWeek": 1, "startTime": "08:00", "endTime": "09:00", "label": "   " } ] }""";
        mockMvc.perform(putBlocks(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLN-ERR-002"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder putBlocks(String body) {
        return put("/api/planner/plan")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
