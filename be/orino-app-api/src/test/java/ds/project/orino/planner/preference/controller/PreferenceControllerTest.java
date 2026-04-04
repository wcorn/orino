package ds.project.orino.planner.preference.controller;

import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.fixedschedule.repository.FixedScheduleRepository;
import ds.project.orino.domain.goal.repository.GoalRepository;
import ds.project.orino.domain.goal.repository.MilestoneRepository;
import ds.project.orino.domain.material.repository.MaterialAllocationRepository;
import ds.project.orino.domain.material.repository.MaterialDailyOverrideRepository;
import ds.project.orino.domain.material.repository.ReviewConfigRepository;
import ds.project.orino.domain.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.material.repository.StudyUnitRepository;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.preference.repository.PriorityRuleRepository;
import ds.project.orino.domain.preference.repository.UserPreferenceRepository;
import ds.project.orino.domain.routine.repository.RoutineCheckRepository;
import ds.project.orino.domain.routine.repository.RoutineExceptionRepository;
import ds.project.orino.domain.routine.repository.RoutineRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PreferenceControllerTest extends ApiTestSupport {

    @Autowired private MemberRepository memberRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private FixedScheduleRepository fixedScheduleRepository;
    @Autowired private RoutineCheckRepository routineCheckRepository;
    @Autowired private RoutineExceptionRepository routineExceptionRepository;
    @Autowired private RoutineRepository routineRepository;
    @Autowired private TodoRepository todoRepository;
    @Autowired private ReviewConfigRepository reviewConfigRepository;
    @Autowired private MaterialDailyOverrideRepository dailyOverrideRepository;
    @Autowired private MaterialAllocationRepository allocationRepository;
    @Autowired private StudyUnitRepository unitRepository;
    @Autowired private StudyMaterialRepository materialRepository;
    @Autowired private PriorityRuleRepository priorityRuleRepository;
    @Autowired private UserPreferenceRepository userPreferenceRepository;

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
                                """.formatted(
                                MemberFixture.DEFAULT_LOGIN_ID,
                                MemberFixture.DEFAULT_PASSWORD)))
                .andReturn();

        accessToken = com.jayway.jsonpath.JsonPath.read(
                loginResult.getResponse().getContentAsString(),
                "$.data.accessToken");
    }

    @Test
    @DisplayName("GET /api/preferences - 설정을 조회한다 (기본값 생성)")
    void getPreference() throws Exception {
        mockMvc.perform(get("/api/preferences")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wakeTime")
                        .value("07:00:00"))
                .andExpect(jsonPath("$.data.focusMinutes")
                        .value(50))
                .andExpect(jsonPath("$.data.dailyStudyMinutes")
                        .value(240));
    }

    @Test
    @DisplayName("PUT /api/preferences - 설정을 수정한다")
    void updatePreference() throws Exception {
        mockMvc.perform(put("/api/preferences")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"wakeTime": "08:00",
                                 "sleepTime": "23:00",
                                 "dailyStudyMinutes": 300,
                                 "studyTimePreference": "EVENING",
                                 "focusMinutes": 45,
                                 "breakMinutes": 15,
                                 "restDays": "SUN",
                                 "skipHolidays": true,
                                 "defaultReviewIntervals": "1,3,7,14",
                                 "defaultMissedPolicy": "SKIP",
                                 "streakFreezePerMonth": 3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyStudyMinutes")
                        .value(300))
                .andExpect(jsonPath("$.data.studyTimePreference")
                        .value("EVENING"))
                .andExpect(jsonPath("$.data.restDays")
                        .value("SUN"));
    }

    @Test
    @DisplayName("GET /api/preferences/priority-rules - 규칙을 조회한다 (기본값 생성)")
    void getPriorityRules() throws Exception {
        mockMvc.perform(get("/api/preferences/priority-rules")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data",
                        hasSize(5)))
                .andExpect(jsonPath("$.data[0].itemType")
                        .value("DEADLINE"));
    }

    @Test
    @DisplayName("PUT /api/preferences/priority-rules - 규칙 순서를 변경한다")
    void updatePriorityRules() throws Exception {
        mockMvc.perform(get("/api/preferences/priority-rules")
                .header("Authorization", "Bearer " + accessToken));

        mockMvc.perform(put("/api/preferences/priority-rules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rules": [
                                  {"itemType": "REVIEW", "sortOrder": 0},
                                  {"itemType": "DEADLINE", "sortOrder": 1},
                                  {"itemType": "STUDY", "sortOrder": 2},
                                  {"itemType": "TODO", "sortOrder": 3},
                                  {"itemType": "ROUTINE", "sortOrder": 4}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].itemType")
                        .value("REVIEW"))
                .andExpect(jsonPath("$.data[1].itemType")
                        .value("DEADLINE"));
    }

    @Test
    @DisplayName("인증 없이 요청하면 403을 반환한다")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/api/preferences"))
                .andExpect(status().isForbidden());
    }
}
