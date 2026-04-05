package ds.project.orino.planner.rescheduling.controller;

import ds.project.orino.domain.calendar.entity.DailySchedule;
import ds.project.orino.domain.calendar.repository.DailyScheduleRepository;
import ds.project.orino.domain.category.entity.Category;
import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.goal.repository.GoalRepository;
import ds.project.orino.domain.goal.repository.MilestoneRepository;
import ds.project.orino.domain.material.entity.DeadlineMode;
import ds.project.orino.domain.material.entity.MaterialAllocation;
import ds.project.orino.domain.material.entity.MaterialType;
import ds.project.orino.domain.material.entity.StudyMaterial;
import ds.project.orino.domain.material.entity.StudyUnit;
import ds.project.orino.domain.material.repository.MaterialAllocationRepository;
import ds.project.orino.domain.material.repository.MaterialDailyOverrideRepository;
import ds.project.orino.domain.material.repository.ReviewConfigRepository;
import ds.project.orino.domain.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.material.repository.StudyUnitRepository;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.preference.entity.UserPreference;
import ds.project.orino.domain.preference.repository.PriorityRuleRepository;
import ds.project.orino.domain.preference.repository.UserPreferenceRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReschedulingControllerTest extends ApiTestSupport {

    @Autowired private MemberRepository memberRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private ReviewConfigRepository reviewConfigRepository;
    @Autowired private MaterialDailyOverrideRepository dailyOverrideRepository;
    @Autowired private MaterialAllocationRepository allocationRepository;
    @Autowired private StudyUnitRepository unitRepository;
    @Autowired private StudyMaterialRepository materialRepository;
    @Autowired private PriorityRuleRepository priorityRuleRepository;
    @Autowired private UserPreferenceRepository userPreferenceRepository;
    @Autowired private DailyScheduleRepository dailyScheduleRepository;

    private String accessToken;
    private Member member;

    @BeforeEach
    void setUp() throws Exception {
        dailyScheduleRepository.deleteAll();
        priorityRuleRepository.deleteAll();
        userPreferenceRepository.deleteAll();
        reviewConfigRepository.deleteAll();
        dailyOverrideRepository.deleteAll();
        allocationRepository.deleteAll();
        unitRepository.deleteAll();
        materialRepository.deleteAll();
        milestoneRepository.deleteAll();
        goalRepository.deleteAll();
        categoryRepository.deleteAll();
        memberRepository.deleteAll();

        member = memberRepository.save(MemberFixture.create());

        UserPreference preference = new UserPreference(member);
        ReflectionTestUtils.setField(preference, "dailyStudyMinutes", 240);
        userPreferenceRepository.save(preference);

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

    @AfterEach
    void tearDown() {
        dailyScheduleRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /api/rescheduling/options - 연속 미완료일 및 3가지 전략 반환")
    void getOptions_returnsAllStrategies() throws Exception {
        createMissedDays(3);
        createDeadlineMaterial("알고리즘", 30, 4, 60);

        mockMvc.perform(get("/api/rescheduling/options")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missedDays").value(3))
                .andExpect(jsonPath("$.data.missedItems")
                        .value(greaterThan(0)))
                .andExpect(jsonPath("$.data.options.length()").value(3))
                .andExpect(jsonPath("$.data.options[0].strategy")
                        .value("POSTPONE"))
                .andExpect(jsonPath("$.data.options[0].label").value("뒤로 밀기"))
                .andExpect(jsonPath(
                        "$.data.options[0].newEstimatedCompletion",
                        notNullValue()))
                .andExpect(jsonPath("$.data.options[1].strategy")
                        .value("COMPRESS"))
                .andExpect(jsonPath(
                        "$.data.options[1].dailyIncreasePercent",
                        notNullValue()))
                .andExpect(jsonPath("$.data.options[2].strategy")
                        .value("KEEP_DEADLINE"));
    }

    @Test
    @DisplayName("GET /api/rescheduling/options - 미완료일 없으면 missedDays=0, options=[]")
    void getOptions_noMissedDays() throws Exception {
        createDeadlineMaterial("알고리즘", 30, 4, 60);

        mockMvc.perform(get("/api/rescheduling/options")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missedDays").value(0))
                .andExpect(jsonPath("$.data.missedItems").value(0));
    }

    @Test
    @DisplayName("POST /api/rescheduling/apply (POSTPONE) - 데드라인이 N일 뒤로 밀린다")
    void apply_postpone_shiftsDeadline() throws Exception {
        createMissedDays(3);
        LocalDate originalDeadline = LocalDate.now().plusDays(30);
        StudyMaterial material = createDeadlineMaterial(
                "알고리즘", 30, 4, 60);
        ReflectionTestUtils.setField(material, "deadline", originalDeadline);
        materialRepository.save(material);

        mockMvc.perform(post("/api/rescheduling/apply")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"strategy": "POSTPONE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.strategy").value("POSTPONE"))
                .andExpect(jsonPath("$.data.affectedDays")
                        .value(greaterThan(0)));

        StudyMaterial updated = materialRepository.findById(material.getId())
                .orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getDeadline())
                .isEqualTo(originalDeadline.plusDays(3));
    }

    @Test
    @DisplayName("POST /api/rescheduling/apply (COMPRESS) - 할당이 증가한다")
    void apply_compress_updatesAllocation() throws Exception {
        createMissedDays(3);
        StudyMaterial material = createDeadlineMaterial(
                "알고리즘", 30, 4, 60);

        mockMvc.perform(post("/api/rescheduling/apply")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"strategy": "COMPRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.strategy").value("COMPRESS"))
                .andExpect(jsonPath("$.data.newDailyStudyMinutes")
                        .value(greaterThan(60)));

        MaterialAllocation allocation = allocationRepository
                .findByMaterialId(material.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(
                allocation.getDefaultMinutes()).isGreaterThan(60);
    }

    @Test
    @DisplayName("POST /api/rescheduling/apply (KEEP_DEADLINE) - 강한 일일 증가")
    void apply_keepDeadline_updatesAllocation() throws Exception {
        createMissedDays(3);
        StudyMaterial material = createDeadlineMaterial(
                "알고리즘", 30, 4, 60);

        mockMvc.perform(post("/api/rescheduling/apply")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"strategy": "KEEP_DEADLINE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.strategy").value("KEEP_DEADLINE"))
                .andExpect(jsonPath("$.data.newDailyStudyMinutes")
                        .value(greaterThan(0)));

        MaterialAllocation allocation = allocationRepository
                .findByMaterialId(material.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(
                allocation.getDefaultMinutes()).isGreaterThan(60);
    }

    @Test
    @DisplayName("POST /api/rescheduling/apply - 데드라인 과목 없으면 INVALID_STATE")
    void apply_noDeadlineMaterials() throws Exception {
        mockMvc.perform(post("/api/rescheduling/apply")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"strategy": "COMPRESS"}
                                """))
                .andExpect(status().isConflict());
    }

    private void createMissedDays(int days) {
        LocalDate today = LocalDate.now();
        for (int i = 1; i <= days; i++) {
            DailySchedule schedule = new DailySchedule(
                    member, today.minusDays(i));
            schedule.markGenerated(4, 1);
            dailyScheduleRepository.save(schedule);
        }
    }

    private StudyMaterial createDeadlineMaterial(
            String title, int unitMinutes, int unitCount,
            int allocationMinutes) {
        Category category = categoryRepository.save(
                new Category(member, "공부", "#8b00ff", null, 1));
        StudyMaterial material = materialRepository.save(new StudyMaterial(
                member, title, MaterialType.BOOK, category, null,
                LocalDate.now().plusDays(30), DeadlineMode.DEADLINE));
        for (int i = 1; i <= unitCount; i++) {
            unitRepository.save(new StudyUnit(
                    material, "챕터" + i, i, unitMinutes, null));
        }
        allocationRepository.save(new MaterialAllocation(
                material, allocationMinutes));
        return material;
    }
}
