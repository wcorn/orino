package ds.project.orino.planner.routine.controller;

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

class RoutineControllerTest extends ApiTestSupport {

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

    private Integer createRoutine() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/routines")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "운동", "durationMinutes": 30,
                                 "recurrenceType": "DAILY",
                                 "startDate": "2026-04-01"}
                                """))
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.id");
    }

    @Test
    @DisplayName("POST /api/routines - 루틴을 생성한다")
    void create() throws Exception {
        mockMvc.perform(post("/api/routines")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "독서", "durationMinutes": 60,
                                 "recurrenceType": "WEEKLY",
                                 "recurrenceDays": "MON,WED,FRI",
                                 "startDate": "2026-04-01",
                                 "skipHolidays": true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("독서"))
                .andExpect(jsonPath("$.data.durationMinutes").value(60))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/routines - 루틴 목록을 조회한다")
    void getRoutines() throws Exception {
        createRoutine();

        mockMvc.perform(get("/api/routines")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].streak").exists());
    }

    @Test
    @DisplayName("GET /api/routines/{id} - 루틴 상세를 조회한다")
    void getRoutine() throws Exception {
        Integer id = createRoutine();

        mockMvc.perform(get("/api/routines/" + id)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("운동"))
                .andExpect(jsonPath("$.data.streak").exists());
    }

    @Test
    @DisplayName("PUT /api/routines/{id} - 루틴을 수정한다")
    void update() throws Exception {
        Integer id = createRoutine();

        mockMvc.perform(put("/api/routines/" + id)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "수정됨", "durationMinutes": 45,
                                 "recurrenceType": "WEEKLY",
                                 "recurrenceDays": "MON,FRI",
                                 "startDate": "2026-04-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정됨"))
                .andExpect(jsonPath("$.data.durationMinutes").value(45));
    }

    @Test
    @DisplayName("DELETE /api/routines/{id} - 루틴을 삭제한다")
    void deleteRoutine() throws Exception {
        Integer id = createRoutine();

        mockMvc.perform(delete("/api/routines/" + id)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/routines")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("PATCH /api/routines/{id}/status - 루틴 상태를 변경한다")
    void changeStatus() throws Exception {
        Integer id = createRoutine();

        mockMvc.perform(patch("/api/routines/" + id + "/status")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "PAUSED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"));
    }

    @Test
    @DisplayName("POST /api/routines/{id}/check - 루틴을 체크한다")
    void check() throws Exception {
        Integer id = createRoutine();

        mockMvc.perform(post("/api/routines/" + id + "/check")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"checkDate": "2026-04-04"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));
    }

    @Test
    @DisplayName("DELETE /api/routines/{id}/check - 루틴 체크를 취소한다")
    void uncheck() throws Exception {
        Integer id = createRoutine();

        mockMvc.perform(post("/api/routines/" + id + "/check")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"checkDate": "2026-04-04"}
                        """));

        mockMvc.perform(delete("/api/routines/" + id + "/check")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("checkDate", "2026-04-04"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/routines/{id}/exceptions - 예외일을 추가한다")
    void addException() throws Exception {
        Integer id = createRoutine();

        mockMvc.perform(post("/api/routines/" + id + "/exceptions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exceptionDate": "2026-04-10"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.exceptionDate")
                        .value("2026-04-10"));
    }

    @Test
    @DisplayName("DELETE /api/routines/{id}/exceptions/{exceptionId} - 예외일을 삭제한다")
    void removeException() throws Exception {
        Integer routineId = createRoutine();

        MvcResult excResult = mockMvc.perform(
                        post("/api/routines/" + routineId + "/exceptions")
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"exceptionDate": "2026-04-10"}
                                        """))
                .andReturn();

        Integer exceptionId = com.jayway.jsonpath.JsonPath.read(
                excResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(delete("/api/routines/" + routineId
                        + "/exceptions/" + exceptionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("존재하지 않는 루틴 조회 시 404를 반환한다")
    void getRoutine_notFound() throws Exception {
        mockMvc.perform(get("/api/routines/999")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));
    }

    @Test
    @DisplayName("인증 없이 요청하면 403을 반환한다")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/api/routines"))
                .andExpect(status().isForbidden());
    }
}
