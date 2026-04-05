package ds.project.orino.planner.calendar.controller;

import ds.project.orino.domain.calendar.repository.DailyScheduleRepository;
import ds.project.orino.domain.category.entity.Category;
import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.fixedschedule.repository.FixedScheduleRepository;
import ds.project.orino.domain.goal.repository.GoalRepository;
import ds.project.orino.domain.goal.repository.MilestoneRepository;
import ds.project.orino.domain.material.entity.DeadlineMode;
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
import ds.project.orino.domain.preference.entity.StudyTimePreference;
import ds.project.orino.domain.preference.entity.UserPreference;
import ds.project.orino.domain.preference.repository.PriorityRuleRepository;
import ds.project.orino.domain.preference.repository.UserPreferenceRepository;
import ds.project.orino.domain.review.repository.ReviewScheduleRepository;
import ds.project.orino.domain.routine.repository.RoutineCheckRepository;
import ds.project.orino.domain.routine.repository.RoutineExceptionRepository;
import ds.project.orino.domain.routine.repository.RoutineRepository;
import ds.project.orino.domain.todo.entity.Priority;
import ds.project.orino.domain.todo.entity.Todo;
import ds.project.orino.domain.todo.repository.TodoRepository;
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
import java.time.LocalTime;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CalendarControllerTest extends ApiTestSupport {

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
    @Autowired private ReviewScheduleRepository reviewScheduleRepository;
    @Autowired private DailyScheduleRepository dailyScheduleRepository;

    private String accessToken;
    private Member member;
    private LocalDate targetDate;

    @BeforeEach
    void setUp() throws Exception {
        dailyScheduleRepository.deleteAll();
        reviewScheduleRepository.deleteAll();
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

        member = memberRepository.save(MemberFixture.create());
        targetDate = LocalDate.now().plusDays(7);

        UserPreference preference = new UserPreference(member);
        ReflectionTestUtils.setField(preference, "wakeTime",
                LocalTime.of(7, 0));
        ReflectionTestUtils.setField(preference, "sleepTime",
                LocalTime.of(23, 0));
        ReflectionTestUtils.setField(preference, "focusMinutes", 50);
        ReflectionTestUtils.setField(preference, "breakMinutes", 10);
        ReflectionTestUtils.setField(preference, "studyTimePreference",
                StudyTimePreference.MORNING);
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
        reviewScheduleRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /api/calendar/daily - 일정을 생성하여 반환한다")
    void getDaily_generatesSchedule() throws Exception {
        Category category = categoryRepository.save(
                new Category(member, "공부", "#8b00ff", null, 1));
        StudyMaterial material = materialRepository.save(new StudyMaterial(
                member, "알고리즘", MaterialType.BOOK, category, null,
                null, DeadlineMode.FREE));
        unitRepository.save(new StudyUnit(material, "챕터1", 1, 30, null));

        mockMvc.perform(get("/api/calendar/daily")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", targetDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value(targetDate.toString()))
                .andExpect(jsonPath("$.data.totalBlocks")
                        .value(greaterThan(0)))
                .andExpect(jsonPath("$.data.blocks[0].blockType").value("STUDY"))
                .andExpect(jsonPath("$.data.blocks[0].categoryName").value("공부"))
                .andExpect(jsonPath("$.data.blocks[0].categoryColor").value("#8b00ff"))
                .andExpect(jsonPath("$.data.blocks[0].title", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/calendar/weekly - 7일분 스케줄을 반환한다")
    void getWeekly_returnsSevenDays() throws Exception {
        Category category = categoryRepository.save(
                new Category(member, "공부", "#8b00ff", null, 1));
        StudyMaterial material = materialRepository.save(new StudyMaterial(
                member, "알고리즘", MaterialType.BOOK, category, null,
                null, DeadlineMode.FREE));
        unitRepository.save(new StudyUnit(material, "챕터1", 1, 30, null));

        mockMvc.perform(get("/api/calendar/weekly")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", targetDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate")
                        .value(targetDate.toString()))
                .andExpect(jsonPath("$.data.endDate")
                        .value(targetDate.plusDays(6).toString()))
                .andExpect(jsonPath("$.data.days.length()").value(7))
                .andExpect(jsonPath("$.data.days[0].date")
                        .value(targetDate.toString()))
                .andExpect(jsonPath("$.data.days[6].date")
                        .value(targetDate.plusDays(6).toString()))
                .andExpect(jsonPath("$.data.days[0].totalBlocks")
                        .value(greaterThan(0)))
                .andExpect(jsonPath("$.data.days[0].blocks[0].blockType")
                        .value("STUDY"));
    }

    @Test
    @DisplayName("GET /api/calendar/weekly - date 파라미터가 없으면 오늘부터 7일")
    void getWeekly_defaultsToToday() throws Exception {
        mockMvc.perform(get("/api/calendar/weekly")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate")
                        .value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.data.days.length()").value(7));
    }

    @Test
    @DisplayName("GET /api/calendar/monthly - 해당 월의 모든 날짜를 반환한다")
    void getMonthly_returnsAllDaysOfMonth() throws Exception {
        Category category = categoryRepository.save(
                new Category(member, "공부", "#8b00ff", null, 1));
        StudyMaterial material = materialRepository.save(new StudyMaterial(
                member, "알고리즘", MaterialType.BOOK, category, null,
                null, DeadlineMode.FREE));
        unitRepository.save(new StudyUnit(material, "챕터1", 1, 30, null));

        int year = targetDate.getYear();
        int month = targetDate.getMonthValue();
        int expectedDays = targetDate.lengthOfMonth();

        mockMvc.perform(get("/api/calendar/monthly")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("year", String.valueOf(year))
                        .param("month", String.valueOf(month)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.year").value(year))
                .andExpect(jsonPath("$.data.month").value(month))
                .andExpect(jsonPath("$.data.days.length()").value(expectedDays));
    }

    @Test
    @DisplayName("PATCH /api/calendar/blocks/{id}/complete (TODO) - 할 일 완료")
    void completeBlock_todo() throws Exception {
        Todo todo = todoRepository.save(new Todo(
                member, "리포트 작성", null, null, null,
                Priority.HIGH, targetDate.plusDays(1), 30));

        MvcResult getResult = mockMvc.perform(get("/api/calendar/daily")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", targetDate.toString()))
                .andExpect(status().isOk())
                .andReturn();
        Integer blockId = com.jayway.jsonpath.JsonPath.read(
                getResult.getResponse().getContentAsString(),
                "$.data.blocks[0].id");
        Integer todoIdInBlock = com.jayway.jsonpath.JsonPath.read(
                getResult.getResponse().getContentAsString(),
                "$.data.blocks[0].referenceId");
        org.assertj.core.api.Assertions.assertThat(todoIdInBlock.longValue())
                .isEqualTo(todo.getId());

        mockMvc.perform(patch("/api/calendar/blocks/" + blockId + "/complete")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.effect.type").value("TODO_COMPLETED"))
                .andExpect(jsonPath("$.data.dailyProgress.completedBlocks")
                        .value(1));
    }

    @Test
    @DisplayName("PATCH complete (STUDY) - 복습 일정이 자동 생성된다")
    void completeBlock_studyCreatesReviews() throws Exception {
        StudyMaterial material = materialRepository.save(new StudyMaterial(
                member, "영어", MaterialType.BOOK, null, null,
                null, DeadlineMode.FREE));
        unitRepository.save(new StudyUnit(material, "Unit1", 1, 30, null));

        MvcResult getResult = mockMvc.perform(get("/api/calendar/daily")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", targetDate.toString()))
                .andReturn();
        Integer blockId = com.jayway.jsonpath.JsonPath.read(
                getResult.getResponse().getContentAsString(),
                "$.data.blocks[0].id");

        mockMvc.perform(patch("/api/calendar/blocks/" + blockId + "/complete")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effect.type").value("REVIEW_CREATED"))
                .andExpect(jsonPath("$.data.effect.nextReviewDate")
                        .value(targetDate.plusDays(1).toString()));

        org.assertj.core.api.Assertions.assertThat(
                reviewScheduleRepository.findAll()).hasSize(6);
    }

    @Test
    @DisplayName("PATCH complete - 이미 완료된 블록은 INVALID_STATE 오류")
    void completeBlock_alreadyCompleted() throws Exception {
        todoRepository.save(new Todo(member, "작업", null, null, null,
                Priority.HIGH, targetDate.plusDays(1), 30));

        MvcResult getResult = mockMvc.perform(get("/api/calendar/daily")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", targetDate.toString()))
                .andReturn();
        Integer blockId = com.jayway.jsonpath.JsonPath.read(
                getResult.getResponse().getContentAsString(),
                "$.data.blocks[0].id");

        mockMvc.perform(patch("/api/calendar/blocks/" + blockId + "/complete")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/calendar/blocks/" + blockId + "/complete")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PUT /api/calendar/blocks/{id}/reorder - 블록 시간 변경하면 pinned=true")
    void reorderBlock() throws Exception {
        todoRepository.save(new Todo(member, "작업", null, null, null,
                Priority.MEDIUM, targetDate.plusDays(1), 30));

        MvcResult getResult = mockMvc.perform(get("/api/calendar/daily")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", targetDate.toString()))
                .andReturn();
        Integer blockId = com.jayway.jsonpath.JsonPath.read(
                getResult.getResponse().getContentAsString(),
                "$.data.blocks[0].id");

        mockMvc.perform(put("/api/calendar/blocks/" + blockId + "/reorder")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startTime": "14:00", "endTime": "14:30"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startTime").value("14:00:00"))
                .andExpect(jsonPath("$.data.endTime").value("14:30:00"))
                .andExpect(jsonPath("$.data.isPinned").value(true));
    }

    @Test
    @DisplayName("PUT reorder - end가 start보다 빠르면 400")
    void reorderBlock_invalidTimeRange() throws Exception {
        todoRepository.save(new Todo(member, "작업", null, null, null,
                Priority.MEDIUM, targetDate.plusDays(1), 30));

        MvcResult getResult = mockMvc.perform(get("/api/calendar/daily")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", targetDate.toString()))
                .andReturn();
        Integer blockId = com.jayway.jsonpath.JsonPath.read(
                getResult.getResponse().getContentAsString(),
                "$.data.blocks[0].id");

        mockMvc.perform(put("/api/calendar/blocks/" + blockId + "/reorder")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startTime": "15:00", "endTime": "14:00"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
