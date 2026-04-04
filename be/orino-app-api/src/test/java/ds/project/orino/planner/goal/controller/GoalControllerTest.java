package ds.project.orino.planner.goal.controller;

import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.fixedschedule.repository.FixedScheduleRepository;
import ds.project.orino.domain.goal.repository.GoalRepository;
import ds.project.orino.domain.goal.repository.MilestoneRepository;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoalControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private FixedScheduleRepository fixedScheduleRepository;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        fixedScheduleRepository.deleteAll();
        milestoneRepository.deleteAll();
        goalRepository.deleteAll();
        categoryRepository.deleteAll();
        memberRepository.deleteAll();
        memberRepository.save(MemberFixture.create());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "%s", "password": "%s"}
                                """.formatted(MemberFixture.DEFAULT_LOGIN_ID, MemberFixture.DEFAULT_PASSWORD)))
                .andReturn();

        accessToken = com.jayway.jsonpath.JsonPath.read(
                loginResult.getResponse().getContentAsString(), "$.data.accessToken");
    }

    @Test
    @DisplayName("POST /api/goals - 목표를 생성한다")
    void create() throws Exception {
        mockMvc.perform(post("/api/goals")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "알고리즘 마스터", "periodType": "QUARTER",
                                 "startDate": "2026-04-01", "deadline": "2026-06-30"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("알고리즘 마스터"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/goals - 목표 목록을 조회한다")
    void getGoals() throws Exception {
        mockMvc.perform(post("/api/goals")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "목표1", "periodType": "QUARTER",
                         "startDate": "2026-04-01"}
                        """));
        mockMvc.perform(post("/api/goals")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "목표2", "periodType": "YEAR",
                         "startDate": "2026-01-01"}
                        """));

        mockMvc.perform(get("/api/goals")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/goals/{id} - 목표 상세를 조회한다")
    void getGoal() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/goals")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "상세 목표", "description": "설명입니다",
                                 "periodType": "HALF_YEAR", "startDate": "2026-01-01",
                                 "deadline": "2026-06-30"}
                                """))
                .andReturn();

        Integer goalId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/goals/" + goalId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("상세 목표"))
                .andExpect(jsonPath("$.data.description").value("설명입니다"))
                .andExpect(jsonPath("$.data.milestones").isArray());
    }

    @Test
    @DisplayName("PUT /api/goals/{id} - 목표를 수정한다")
    void update() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/goals")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "기존 목표", "periodType": "QUARTER",
                                 "startDate": "2026-04-01"}
                                """))
                .andReturn();

        Integer goalId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(put("/api/goals/" + goalId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "수정된 목표", "periodType": "YEAR",
                                 "startDate": "2026-01-01", "deadline": "2026-12-31"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 목표"))
                .andExpect(jsonPath("$.data.periodType").value("YEAR"));
    }

    @Test
    @DisplayName("DELETE /api/goals/{id} - 목표를 삭제한다")
    void deleteGoal() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/goals")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "삭제대상", "periodType": "QUARTER",
                                 "startDate": "2026-04-01"}
                                """))
                .andReturn();

        Integer goalId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(delete("/api/goals/" + goalId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/goals")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("PATCH /api/goals/{id}/status - 목표 상태를 변경한다")
    void changeStatus() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/goals")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "상태변경", "periodType": "QUARTER",
                                 "startDate": "2026-04-01"}
                                """))
                .andReturn();

        Integer goalId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(patch("/api/goals/" + goalId + "/status")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "COMPLETED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("POST /api/goals/{id}/milestones - 마일스톤을 추가한다")
    void createMilestone() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/goals")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "목표", "periodType": "QUARTER",
                                 "startDate": "2026-04-01"}
                                """))
                .andReturn();

        Integer goalId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/goals/" + goalId + "/milestones")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "중간 목표", "deadline": "2026-05-01", "sortOrder": 0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("중간 목표"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("PUT /api/goals/{goalId}/milestones/{id} - 마일스톤을 수정한다")
    void updateMilestone() throws Exception {
        MvcResult goalResult = mockMvc.perform(post("/api/goals")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "목표", "periodType": "QUARTER",
                                 "startDate": "2026-04-01"}
                                """))
                .andReturn();

        Integer goalId = com.jayway.jsonpath.JsonPath.read(
                goalResult.getResponse().getContentAsString(), "$.data.id");

        MvcResult milestoneResult = mockMvc.perform(post("/api/goals/" + goalId + "/milestones")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "기존 마일스톤", "sortOrder": 0}
                                """))
                .andReturn();

        Integer milestoneId = com.jayway.jsonpath.JsonPath.read(
                milestoneResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(put("/api/goals/" + goalId + "/milestones/" + milestoneId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "수정된 마일스톤", "deadline": "2026-05-15", "sortOrder": 1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 마일스톤"))
                .andExpect(jsonPath("$.data.sortOrder").value(1));
    }

    @Test
    @DisplayName("DELETE /api/goals/{goalId}/milestones/{id} - 마일스톤을 삭제한다")
    void deleteMilestone() throws Exception {
        MvcResult goalResult = mockMvc.perform(post("/api/goals")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "목표", "periodType": "QUARTER",
                                 "startDate": "2026-04-01"}
                                """))
                .andReturn();

        Integer goalId = com.jayway.jsonpath.JsonPath.read(
                goalResult.getResponse().getContentAsString(), "$.data.id");

        MvcResult milestoneResult = mockMvc.perform(post("/api/goals/" + goalId + "/milestones")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "삭제대상", "sortOrder": 0}
                                """))
                .andReturn();

        Integer milestoneId = com.jayway.jsonpath.JsonPath.read(
                milestoneResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(delete("/api/goals/" + goalId + "/milestones/" + milestoneId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/goals/" + goalId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data.milestones", hasSize(0)));
    }

    @Test
    @DisplayName("PATCH /api/goals/{goalId}/milestones/{id}/complete - 마일스톤을 완료 처리한다")
    void completeMilestone() throws Exception {
        MvcResult goalResult = mockMvc.perform(post("/api/goals")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "목표", "periodType": "QUARTER",
                                 "startDate": "2026-04-01"}
                                """))
                .andReturn();

        Integer goalId = com.jayway.jsonpath.JsonPath.read(
                goalResult.getResponse().getContentAsString(), "$.data.id");

        MvcResult milestoneResult = mockMvc.perform(post("/api/goals/" + goalId + "/milestones")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "완료할 마일스톤", "sortOrder": 0}
                                """))
                .andReturn();

        Integer milestoneId = com.jayway.jsonpath.JsonPath.read(
                milestoneResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(patch("/api/goals/" + goalId + "/milestones/" + milestoneId + "/complete")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /api/goals/{id} - 존재하지 않는 목표이면 404를 반환한다")
    void getGoal_notFound() throws Exception {
        mockMvc.perform(get("/api/goals/999")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));
    }

    @Test
    @DisplayName("POST /api/goals - 잘못된 periodType이면 400을 반환한다")
    void create_invalidPeriodType() throws Exception {
        mockMvc.perform(post("/api/goals")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "목표", "periodType": "INVALID",
                                 "startDate": "2026-04-01"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 없이 요청하면 403을 반환한다")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/api/goals"))
                .andExpect(status().isForbidden());
    }
}
