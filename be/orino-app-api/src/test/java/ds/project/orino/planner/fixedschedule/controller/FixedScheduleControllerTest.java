package ds.project.orino.planner.fixedschedule.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FixedScheduleControllerTest extends ApiTestSupport {

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

    @Test
    @DisplayName("POST /api/fixed-schedules - 단발성 고정 일정을 생성한다")
    void create_single() throws Exception {
        mockMvc.perform(post("/api/fixed-schedules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "면접", "startTime": "14:00",
                                 "endTime": "15:00", "scheduleDate": "2026-04-15",
                                 "recurrenceType": "NONE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("면접"))
                .andExpect(jsonPath("$.data.recurrenceType").value("NONE"));
    }

    @Test
    @DisplayName("POST /api/fixed-schedules - 주간 반복 일정을 생성한다")
    void create_weekly() throws Exception {
        mockMvc.perform(post("/api/fixed-schedules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "운동", "startTime": "07:00",
                                 "endTime": "08:00",
                                 "recurrenceType": "WEEKLY",
                                 "recurrenceDays": "MON,WED,FRI",
                                 "recurrenceStart": "2026-04-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recurrenceType").value("WEEKLY"))
                .andExpect(jsonPath("$.data.recurrenceDays").value("MON,WED,FRI"));
    }

    @Test
    @DisplayName("GET /api/fixed-schedules - 고정 일정 목록을 조회한다")
    void getFixedSchedules() throws Exception {
        mockMvc.perform(post("/api/fixed-schedules")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "일정1", "startTime": "09:00",
                         "endTime": "10:00", "scheduleDate": "2026-04-10",
                         "recurrenceType": "NONE"}
                        """));
        mockMvc.perform(post("/api/fixed-schedules")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "일정2", "startTime": "11:00",
                         "endTime": "12:00", "scheduleDate": "2026-04-11",
                         "recurrenceType": "NONE"}
                        """));

        mockMvc.perform(get("/api/fixed-schedules")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @DisplayName("PUT /api/fixed-schedules/{id} - 고정 일정을 수정한다")
    void update() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/fixed-schedules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "기존", "startTime": "09:00",
                                 "endTime": "10:00", "scheduleDate": "2026-04-10",
                                 "recurrenceType": "NONE"}
                                """))
                .andReturn();

        Integer id = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(put("/api/fixed-schedules/" + id)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "수정됨", "startTime": "10:00",
                                 "endTime": "11:30", "scheduleDate": "2026-04-20",
                                 "recurrenceType": "NONE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정됨"))
                .andExpect(jsonPath("$.data.startTime").value("10:00:00"));
    }

    @Test
    @DisplayName("DELETE /api/fixed-schedules/{id} - 고정 일정을 삭제한다")
    void deleteSchedule() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/fixed-schedules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "삭제대상", "startTime": "09:00",
                                 "endTime": "10:00", "scheduleDate": "2026-04-10",
                                 "recurrenceType": "NONE"}
                                """))
                .andReturn();

        Integer id = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(delete("/api/fixed-schedules/" + id)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/fixed-schedules")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("NONE 타입에 scheduleDate 없으면 400을 반환한다")
    void create_noneWithoutDate() throws Exception {
        mockMvc.perform(post("/api/fixed-schedules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "테스트", "startTime": "09:00",
                                 "endTime": "10:00",
                                 "recurrenceType": "NONE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }

    @Test
    @DisplayName("존재하지 않는 고정 일정 수정 시 404를 반환한다")
    void update_notFound() throws Exception {
        mockMvc.perform(put("/api/fixed-schedules/999")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "이름", "startTime": "09:00",
                                 "endTime": "10:00", "scheduleDate": "2026-04-10",
                                 "recurrenceType": "NONE"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));
    }

    @Test
    @DisplayName("인증 없이 요청하면 403을 반환한다")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/api/fixed-schedules"))
                .andExpect(status().isForbidden());
    }
}
