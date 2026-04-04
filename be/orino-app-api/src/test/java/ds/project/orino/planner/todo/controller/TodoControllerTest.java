package ds.project.orino.planner.todo.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TodoControllerTest extends ApiTestSupport {

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

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
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

    private Integer createTodo() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/todos")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "할 일1", "priority": "HIGH",
                                 "deadline": "2026-04-10",
                                 "estimatedMinutes": 60}
                                """))
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.id");
    }

    @Test
    @DisplayName("POST /api/todos - 할 일을 생성한다")
    void create() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "독서", "description": "1시간 읽기",
                                 "priority": "MEDIUM",
                                 "deadline": "2026-04-15",
                                 "estimatedMinutes": 60}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("독서"))
                .andExpect(jsonPath("$.data.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/todos - 할 일 목록을 조회한다")
    void getTodos() throws Exception {
        createTodo();

        mockMvc.perform(get("/api/todos")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("할 일1"));
    }

    @Test
    @DisplayName("GET /api/todos?status=PENDING - 상태별 필터링한다")
    void getTodos_filterByStatus() throws Exception {
        createTodo();

        mockMvc.perform(get("/api/todos")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/todos?priority=HIGH - 우선순위별 필터링한다")
    void getTodos_filterByPriority() throws Exception {
        createTodo();

        mockMvc.perform(get("/api/todos")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("priority", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        mockMvc.perform(get("/api/todos")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("priority", "LOW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("PUT /api/todos/{id} - 할 일을 수정한다")
    void update() throws Exception {
        Integer todoId = createTodo();

        mockMvc.perform(put("/api/todos/" + todoId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "수정된 할 일",
                                 "priority": "LOW",
                                 "estimatedMinutes": 30}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 할 일"))
                .andExpect(jsonPath("$.data.priority").value("LOW"))
                .andExpect(jsonPath("$.data.estimatedMinutes").value(30));
    }

    @Test
    @DisplayName("DELETE /api/todos/{id} - 할 일을 삭제한다")
    void deleteTodo() throws Exception {
        Integer todoId = createTodo();

        mockMvc.perform(delete("/api/todos/" + todoId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/todos")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("PATCH /api/todos/{id}/complete - 할 일을 완료 처리한다")
    void complete() throws Exception {
        Integer todoId = createTodo();

        mockMvc.perform(patch("/api/todos/" + todoId + "/complete")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").exists());
    }

    @Test
    @DisplayName("POST /api/todos - 제목 없이 생성하면 400을 반환한다")
    void create_noTitle() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "설명만"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/todos/{id} - 존재하지 않는 할 일이면 404를 반환한다")
    void update_notFound() throws Exception {
        mockMvc.perform(put("/api/todos/999")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "이름"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));
    }

    @Test
    @DisplayName("인증 없이 요청하면 403을 반환한다")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isForbidden());
    }
}
