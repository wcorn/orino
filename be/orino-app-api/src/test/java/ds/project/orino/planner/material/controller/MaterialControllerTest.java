package ds.project.orino.planner.material.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MaterialControllerTest extends ApiTestSupport {

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

    private Integer createMaterial() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/materials")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "이펙티브 자바", "type": "BOOK",
                                 "deadline": "2026-07-01",
                                 "deadlineMode": "DEADLINE"}
                                """))
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data.id");
    }

    // --- Material CRUD ---

    @Test
    @DisplayName("POST /api/materials - 학습 자료를 생성한다")
    void create() throws Exception {
        mockMvc.perform(post("/api/materials")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "이펙티브 자바", "type": "BOOK",
                                 "deadline": "2026-07-01",
                                 "deadlineMode": "DEADLINE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title")
                        .value("이펙티브 자바"))
                .andExpect(jsonPath("$.data.type").value("BOOK"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/materials - 인라인 학습 단위와 함께 생성한다")
    void create_withUnits() throws Exception {
        mockMvc.perform(post("/api/materials")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "이펙티브 자바", "type": "BOOK",
                                 "units": [
                                   {"title": "아이템 1", "sortOrder": 0,
                                    "estimatedMinutes": 30},
                                   {"title": "아이템 2", "sortOrder": 1,
                                    "estimatedMinutes": 30}
                                 ]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalUnits").value(2));
    }

    @Test
    @DisplayName("GET /api/materials - 학습 자료 목록을 조회한다")
    void getMaterials() throws Exception {
        createMaterial();

        mockMvc.perform(get("/api/materials")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title")
                        .value("이펙티브 자바"));
    }

    @Test
    @DisplayName("GET /api/materials/{id} - 상세를 조회한다")
    void getMaterial() throws Exception {
        Integer id = createMaterial();

        mockMvc.perform(get("/api/materials/" + id)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title")
                        .value("이펙티브 자바"))
                .andExpect(jsonPath("$.data.units").isArray());
    }

    @Test
    @DisplayName("PUT /api/materials/{id} - 학습 자료를 수정한다")
    void update() throws Exception {
        Integer id = createMaterial();

        mockMvc.perform(put("/api/materials/" + id)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "수정됨", "type": "LECTURE",
                                 "deadlineMode": "FREE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정됨"))
                .andExpect(jsonPath("$.data.type").value("LECTURE"));
    }

    @Test
    @DisplayName("DELETE /api/materials/{id} - 학습 자료를 삭제한다")
    void deleteMaterial() throws Exception {
        Integer id = createMaterial();

        mockMvc.perform(delete("/api/materials/" + id)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/materials")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("PATCH /api/materials/{id}/pause - 일시정지한다")
    void pause() throws Exception {
        Integer id = createMaterial();

        mockMvc.perform(patch("/api/materials/" + id + "/pause")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"));
    }

    @Test
    @DisplayName("PATCH /api/materials/{id}/resume - 재개한다")
    void resume() throws Exception {
        Integer id = createMaterial();

        mockMvc.perform(patch("/api/materials/" + id + "/pause")
                .header("Authorization", "Bearer " + accessToken));

        mockMvc.perform(patch("/api/materials/" + id + "/resume")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("ACTIVE 상태에서 resume하면 409를 반환한다")
    void resume_invalidState() throws Exception {
        Integer id = createMaterial();

        mockMvc.perform(patch("/api/materials/" + id + "/resume")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SP-ERR-004"));
    }

    // --- Study Unit ---

    @Test
    @DisplayName("POST /api/materials/{id}/units - 학습 단위를 추가한다")
    void createUnit() throws Exception {
        Integer materialId = createMaterial();

        mockMvc.perform(post("/api/materials/" + materialId + "/units")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "아이템 1",
                                 "estimatedMinutes": 30,
                                 "sortOrder": 0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title")
                        .value("아이템 1"));
    }

    @Test
    @DisplayName("POST /api/materials/{id}/units/batch - 일괄 등록한다")
    void createUnitsBatch() throws Exception {
        Integer materialId = createMaterial();

        mockMvc.perform(post("/api/materials/" + materialId
                        + "/units/batch")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"units": [
                                  {"title": "아이템 1", "sortOrder": 0,
                                   "estimatedMinutes": 30},
                                  {"title": "아이템 2", "sortOrder": 1,
                                   "estimatedMinutes": 45}
                                ]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @DisplayName("PUT /api/materials/{materialId}/units/{id} - 학습 단위를 수정한다")
    void updateUnit() throws Exception {
        Integer materialId = createMaterial();

        MvcResult unitResult = mockMvc.perform(
                        post("/api/materials/" + materialId + "/units")
                                .header("Authorization",
                                        "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"title": "기존", "sortOrder": 0,
                                         "estimatedMinutes": 30}
                                        """))
                .andReturn();
        Integer unitId = com.jayway.jsonpath.JsonPath.read(
                unitResult.getResponse().getContentAsString(),
                "$.data.id");

        mockMvc.perform(put("/api/materials/" + materialId
                        + "/units/" + unitId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "수정됨", "sortOrder": 1,
                                 "estimatedMinutes": 60}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정됨"))
                .andExpect(jsonPath("$.data.estimatedMinutes")
                        .value(60));
    }

    @Test
    @DisplayName("DELETE /api/materials/{materialId}/units/{id} - 학습 단위를 삭제한다")
    void deleteUnit() throws Exception {
        Integer materialId = createMaterial();

        MvcResult unitResult = mockMvc.perform(
                        post("/api/materials/" + materialId + "/units")
                                .header("Authorization",
                                        "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"title": "삭제대상",
                                         "sortOrder": 0,
                                         "estimatedMinutes": 30}
                                        """))
                .andReturn();
        Integer unitId = com.jayway.jsonpath.JsonPath.read(
                unitResult.getResponse().getContentAsString(),
                "$.data.id");

        mockMvc.perform(delete("/api/materials/" + materialId
                        + "/units/" + unitId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    // --- Allocation ---

    @Test
    @DisplayName("PUT /api/materials/{id}/allocation - 시간 할당을 설정한다")
    void updateAllocation() throws Exception {
        Integer materialId = createMaterial();

        mockMvc.perform(put("/api/materials/" + materialId
                        + "/allocation")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultMinutes": 60}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultMinutes")
                        .value(60));
    }

    // --- Daily Override ---

    @Test
    @DisplayName("PUT /api/materials/{id}/daily-overrides/{date} - 일별 오버라이드를 설정한다")
    void updateDailyOverride() throws Exception {
        Integer materialId = createMaterial();

        mockMvc.perform(put("/api/materials/" + materialId
                        + "/daily-overrides/2026-04-10")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"minutes": 120}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overrideDate")
                        .value("2026-04-10"))
                .andExpect(jsonPath("$.data.minutes").value(120));
    }

    @Test
    @DisplayName("GET /api/materials/{id}/daily-overrides - 일별 오버라이드 목록을 조회한다")
    void getDailyOverrides() throws Exception {
        Integer materialId = createMaterial();

        mockMvc.perform(put("/api/materials/" + materialId
                        + "/daily-overrides/2026-04-10")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"minutes": 120}
                        """));

        mockMvc.perform(get("/api/materials/" + materialId
                        + "/daily-overrides")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("DELETE /api/materials/{id}/daily-overrides/{date} - 일별 오버라이드를 삭제한다")
    void deleteDailyOverride() throws Exception {
        Integer materialId = createMaterial();

        mockMvc.perform(put("/api/materials/" + materialId
                        + "/daily-overrides/2026-04-10")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"minutes": 120}
                        """));

        mockMvc.perform(delete("/api/materials/" + materialId
                        + "/daily-overrides/2026-04-10")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    // --- Review Config ---

    @Test
    @DisplayName("PUT /api/materials/{id}/review-config - 복습 설정을 저장한다")
    void updateReviewConfig() throws Exception {
        Integer materialId = createMaterial();

        mockMvc.perform(put("/api/materials/" + materialId
                        + "/review-config")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"intervals": "1,3,7,14,30",
                                 "missedPolicy": "IMMEDIATE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intervals")
                        .value("1,3,7,14,30"))
                .andExpect(jsonPath("$.data.missedPolicy")
                        .value("IMMEDIATE"));
    }

    // --- Error cases ---

    @Test
    @DisplayName("존재하지 않는 자료 조회 시 404를 반환한다")
    void getMaterial_notFound() throws Exception {
        mockMvc.perform(get("/api/materials/999")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));
    }

    @Test
    @DisplayName("인증 없이 요청하면 403을 반환한다")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/api/materials"))
                .andExpect(status().isForbidden());
    }
}
