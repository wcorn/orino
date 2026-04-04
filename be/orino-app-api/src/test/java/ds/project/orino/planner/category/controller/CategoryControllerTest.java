package ds.project.orino.planner.category.controller;

import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.fixedschedule.repository.FixedScheduleRepository;
import ds.project.orino.domain.goal.repository.GoalRepository;
import ds.project.orino.domain.goal.repository.MilestoneRepository;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.routine.repository.RoutineCheckRepository;
import ds.project.orino.domain.routine.repository.RoutineExceptionRepository;
import ds.project.orino.domain.routine.repository.RoutineRepository;
import ds.project.orino.domain.material.repository.MaterialAllocationRepository;
import ds.project.orino.domain.material.repository.MaterialDailyOverrideRepository;
import ds.project.orino.domain.material.repository.ReviewConfigRepository;
import ds.project.orino.domain.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.material.repository.StudyUnitRepository;
import ds.project.orino.domain.preference.repository.PriorityRuleRepository;
import ds.project.orino.domain.preference.repository.UserPreferenceRepository;
import ds.project.orino.domain.todo.repository.TodoRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryControllerTest extends ApiTestSupport {

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

    @Autowired
    private RoutineExceptionRepository routineExceptionRepository;

    @Autowired
    private RoutineCheckRepository routineCheckRepository;

    @Autowired
    private RoutineRepository routineRepository;

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private ReviewConfigRepository reviewConfigRepository;

    @Autowired
    private MaterialDailyOverrideRepository dailyOverrideRepository;

    @Autowired
    private MaterialAllocationRepository allocationRepository;

    @Autowired
    private StudyUnitRepository unitRepository;

    @Autowired
    private StudyMaterialRepository materialRepository;

    @Autowired
    private PriorityRuleRepository priorityRuleRepository;

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        priorityRuleRepository.deleteAll();
        userPreferenceRepository.deleteAll();
        reviewConfigRepository.deleteAll();
        dailyOverrideRepository.deleteAll();
        allocationRepository.deleteAll();
        unitRepository.deleteAll();
        materialRepository.deleteAll();
        routineExceptionRepository.deleteAll();
        routineCheckRepository.deleteAll();
        routineRepository.deleteAll();
        fixedScheduleRepository.deleteAll();
        todoRepository.deleteAll();
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
    @DisplayName("POST /api/categories - 카테고리를 생성한다")
    void create() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "프로그래밍", "color": "#FF9800", "icon": "code", "sortOrder": 0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("프로그래밍"))
                .andExpect(jsonPath("$.data.color").value("#FF9800"));
    }

    @Test
    @DisplayName("GET /api/categories - 카테고리 목록을 조회한다")
    void getCategories() throws Exception {
        mockMvc.perform(post("/api/categories")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "프로그래밍", "color": "#FF9800", "icon": "code", "sortOrder": 0}
                        """));
        mockMvc.perform(post("/api/categories")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "알고리즘", "color": "#9C27B0", "icon": "puzzle", "sortOrder": 1}
                        """));

        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].name").value("프로그래밍"))
                .andExpect(jsonPath("$.data[1].name").value("알고리즘"));
    }

    @Test
    @DisplayName("PUT /api/categories/{id} - 카테고리를 수정한다")
    void update() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "기존이름", "color": "#000000", "sortOrder": 0}
                                """))
                .andReturn();

        Integer categoryId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(put("/api/categories/" + categoryId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "새이름", "color": "#FF0000", "icon": "star", "sortOrder": 5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새이름"))
                .andExpect(jsonPath("$.data.color").value("#FF0000"))
                .andExpect(jsonPath("$.data.sortOrder").value(5));
    }

    @Test
    @DisplayName("DELETE /api/categories/{id} - 카테고리를 삭제한다")
    void deleteCategory() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "삭제대상", "color": "#000000", "sortOrder": 0}
                                """))
                .andReturn();

        Integer categoryId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(delete("/api/categories/" + categoryId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("POST /api/categories - 잘못된 color 형식이면 400을 반환한다")
    void create_invalidColor() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "테스트", "color": "invalid", "sortOrder": 0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/categories/{id} - 존재하지 않는 카테고리이면 404를 반환한다")
    void update_notFound() throws Exception {
        mockMvc.perform(put("/api/categories/999")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "이름", "color": "#000000", "sortOrder": 0}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));
    }

    @Test
    @DisplayName("인증 없이 요청하면 403을 반환한다")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isForbidden());
    }
}
