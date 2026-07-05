package ds.project.orino.planner.goal.controller;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.goal.entity.MonthlyGoal;
import ds.project.orino.domain.planner.goal.repository.MonthlyGoalRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MonthlyGoalControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MonthlyGoalRepository monthlyGoalRepository;

    @Autowired
    private DbCleaner dbCleaner;

    private Member member;
    private Member otherMember;
    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        member = memberRepository.save(MemberFixture.create());
        otherMember = memberRepository.save(MemberFixture.create("other", "password"));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    @Test
    @DisplayName("GET - 목표가 없으면 data:null")
    void get_none_returns_null() throws Exception {
        mockMvc.perform(get("/api/planner/monthly-goals/{y}/{m}", 2026, 7)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("PUT - 신규 생성 후 GET으로 조회된다")
    void upsert_creates() throws Exception {
        mockMvc.perform(put("/api/planner/monthly-goals/{y}/{m}", 2026, 7)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"운동 꾸준히\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.year").value(2026))
                .andExpect(jsonPath("$.data.month").value(7))
                .andExpect(jsonPath("$.data.content").value("운동 꾸준히"));

        mockMvc.perform(get("/api/planner/monthly-goals/{y}/{m}", 2026, 7)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.content").value("운동 꾸준히"));
    }

    @Test
    @DisplayName("PUT - 같은 년월 재요청은 같은 행을 갱신한다(중복 생성 없음)")
    void upsert_updates_same_row() throws Exception {
        upsertGoal(2026, 7, "첫 목표");
        upsertGoal(2026, 7, "바뀐 목표");

        mockMvc.perform(get("/api/planner/monthly-goals/{y}/{m}", 2026, 7)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.content").value("바뀐 목표"));
        assertThat(monthlyGoalRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("PUT - 공백만 있는 content는 400")
    void upsert_blank_400() throws Exception {
        mockMvc.perform(put("/api/planner/monthly-goals/{y}/{m}", 2026, 7)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }

    @Test
    @DisplayName("PUT - 1000자 초과 content는 400")
    void upsert_too_long_400() throws Exception {
        String tooLong = "가".repeat(1001);
        mockMvc.perform(put("/api/planner/monthly-goals/{y}/{m}", 2026, 7)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT - month 범위(1~12) 위반은 400")
    void upsert_month_out_of_range_400() throws Exception {
        mockMvc.perform(put("/api/planner/monthly-goals/{y}/{m}", 2026, 13)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"목표\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }

    @Test
    @DisplayName("DELETE - 목표를 제거하면 GET은 null")
    void delete_removes() throws Exception {
        upsertGoal(2026, 7, "지울 목표");

        mockMvc.perform(delete("/api/planner/monthly-goals/{y}/{m}", 2026, 7)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/planner/monthly-goals/{y}/{m}", 2026, 7)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("DELETE - 목표가 없어도 200(idempotent)")
    void delete_idempotent() throws Exception {
        mockMvc.perform(delete("/api/planner/monthly-goals/{y}/{m}", 2026, 7)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("타인 격리 - 다른 멤버의 목표는 보이지 않고 각자 저장된다")
    void isolation_between_members() throws Exception {
        // 타인이 같은 년월에 목표 저장
        monthlyGoalRepository.save(new MonthlyGoal(otherMember.getId(), 2026, 7, "남의 목표"));

        // 내 조회는 null
        mockMvc.perform(get("/api/planner/monthly-goals/{y}/{m}", 2026, 7)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 내가 같은 년월에 저장 → 별도 행
        upsertGoal(2026, 7, "내 목표");
        mockMvc.perform(get("/api/planner/monthly-goals/{y}/{m}", 2026, 7)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.content").value("내 목표"));
        assertThat(monthlyGoalRepository.count()).isEqualTo(2);
    }

    private void upsertGoal(int year, int month, String content) throws Exception {
        mockMvc.perform(put("/api/planner/monthly-goals/{y}/{m}", year, month)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"" + content + "\"}"))
                .andExpect(status().isOk());
    }
}
