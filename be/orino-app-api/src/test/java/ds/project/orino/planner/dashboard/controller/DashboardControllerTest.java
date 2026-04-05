package ds.project.orino.planner.dashboard.controller;

import ds.project.orino.domain.calendar.entity.BlockType;
import ds.project.orino.domain.calendar.entity.DailySchedule;
import ds.project.orino.domain.calendar.entity.ScheduleBlock;
import ds.project.orino.domain.calendar.repository.DailyScheduleRepository;
import ds.project.orino.domain.calendar.repository.ScheduleBlockRepository;
import ds.project.orino.domain.category.entity.Category;
import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.fixedschedule.entity.RecurrenceType;
import ds.project.orino.domain.goal.entity.Goal;
import ds.project.orino.domain.goal.entity.Milestone;
import ds.project.orino.domain.goal.entity.PeriodType;
import ds.project.orino.domain.goal.repository.GoalRepository;
import ds.project.orino.domain.goal.repository.MilestoneRepository;
import ds.project.orino.domain.material.entity.DeadlineMode;
import ds.project.orino.domain.material.entity.MaterialType;
import ds.project.orino.domain.material.entity.StudyMaterial;
import ds.project.orino.domain.material.entity.StudyUnit;
import ds.project.orino.domain.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.material.repository.StudyUnitRepository;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.preference.entity.UserPreference;
import ds.project.orino.domain.preference.repository.PriorityRuleRepository;
import ds.project.orino.domain.preference.repository.UserPreferenceRepository;
import ds.project.orino.domain.routine.entity.Routine;
import ds.project.orino.domain.routine.repository.RoutineRepository;
import ds.project.orino.domain.streak.entity.Streak;
import ds.project.orino.domain.streak.repository.StreakRepository;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest extends ApiTestSupport {

    @Autowired private MemberRepository memberRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private RoutineRepository routineRepository;
    @Autowired private StudyUnitRepository unitRepository;
    @Autowired private StudyMaterialRepository materialRepository;
    @Autowired private PriorityRuleRepository priorityRuleRepository;
    @Autowired private UserPreferenceRepository userPreferenceRepository;
    @Autowired private DailyScheduleRepository dailyScheduleRepository;
    @Autowired private ScheduleBlockRepository scheduleBlockRepository;
    @Autowired private StreakRepository streakRepository;

    private String accessToken;
    private Member member;

    @BeforeEach
    void setUp() throws Exception {
        streakRepository.deleteAll();
        scheduleBlockRepository.deleteAll();
        dailyScheduleRepository.deleteAll();
        priorityRuleRepository.deleteAll();
        userPreferenceRepository.deleteAll();
        unitRepository.deleteAll();
        materialRepository.deleteAll();
        routineRepository.deleteAll();
        milestoneRepository.deleteAll();
        goalRepository.deleteAll();
        categoryRepository.deleteAll();
        memberRepository.deleteAll();

        member = memberRepository.save(MemberFixture.create());
        UserPreference pref = new UserPreference(member);
        ReflectionTestUtils.setField(pref, "streakFreezePerMonth", 3);
        userPreferenceRepository.save(pref);

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
        streakRepository.deleteAll();
        scheduleBlockRepository.deleteAll();
        dailyScheduleRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /api/dashboard/summary - 빈 상태는 기본값 반환")
    void summary_empty() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goals").isArray())
                .andExpect(jsonPath("$.data.thisWeek.studyMinutes").value(0))
                .andExpect(jsonPath("$.data.thisWeek.reviewCompletionRate").value(0))
                .andExpect(jsonPath("$.data.streaks.overall.currentCount").value(0))
                .andExpect(jsonPath("$.data.streaks.overall.longestCount").value(0))
                .andExpect(jsonPath("$.data.streaks.routines").isArray())
                .andExpect(jsonPath("$.data.todayProgress.totalBlocks").value(0))
                .andExpect(jsonPath("$.data.todayProgress.completedBlocks").value(0));
    }

    @Test
    @DisplayName("GET /api/dashboard/summary - 목표 진행률과 오늘 블록 반환")
    void summary_withGoalsAndToday() throws Exception {
        Goal goal = goalRepository.save(new Goal(
                member, null, "백엔드 취업", "설명",
                PeriodType.YEAR, LocalDate.now(), LocalDate.now().plusMonths(6)));
        Milestone m1 = new Milestone(goal, "JPA 완성", LocalDate.now(), 0);
        m1.complete();
        milestoneRepository.save(m1);
        milestoneRepository.save(new Milestone(
                goal, "Spring Security", LocalDate.now(), 1));

        DailySchedule today = dailyScheduleRepository.save(
                new DailySchedule(member, LocalDate.now()));
        today.markGenerated(4, 2);
        dailyScheduleRepository.save(today);

        mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goals", hasSize(1)))
                .andExpect(jsonPath("$.data.goals[0].title").value("백엔드 취업"))
                .andExpect(jsonPath("$.data.goals[0].progressPercent").value(50))
                .andExpect(jsonPath("$.data.goals[0].paceStatus").value("ON_TRACK"))
                .andExpect(jsonPath("$.data.todayProgress.totalBlocks").value(4))
                .andExpect(jsonPath("$.data.todayProgress.completedBlocks").value(2));
    }

    @Test
    @DisplayName("GET /api/dashboard/streaks - 스트릭과 프리즈 잔여 반환")
    void streaks_withOverallAndRoutine() throws Exception {
        Streak overall = Streak.overall(member);
        ReflectionTestUtils.setField(overall, "currentCount", 10);
        ReflectionTestUtils.setField(overall, "longestCount", 20);
        ReflectionTestUtils.setField(overall, "freezeUsedThisMonth", 1);
        streakRepository.save(overall);

        Routine routine = routineRepository.save(new Routine(
                member, "헬스", null, 60, LocalTime.of(7, 0),
                RecurrenceType.WEEKLY,
                null, "MON,WED,FRI", LocalDate.now(), null, false));
        Streak routineStreak = Streak.routine(member, routine);
        ReflectionTestUtils.setField(routineStreak, "currentCount", 5);
        ReflectionTestUtils.setField(routineStreak, "longestCount", 8);
        streakRepository.save(routineStreak);

        mockMvc.perform(get("/api/dashboard/streaks")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall.currentCount").value(10))
                .andExpect(jsonPath("$.data.overall.longestCount").value(20))
                .andExpect(jsonPath("$.data.routines", hasSize(1)))
                .andExpect(jsonPath("$.data.routines[0].title").value("헬스"))
                .andExpect(jsonPath("$.data.routines[0].currentCount").value(5))
                .andExpect(jsonPath("$.data.routines[0].longestCount").value(8))
                .andExpect(jsonPath("$.data.freezeRemaining").value(2));
    }

    @Test
    @DisplayName("GET /api/dashboard/heatmap - 연간 일별 달성률 반환")
    void heatmap_returnsDaysForYear() throws Exception {
        int year = LocalDate.now().getYear();
        LocalDate d1 = LocalDate.of(year, 6, 1);
        LocalDate d2 = LocalDate.of(year, 6, 2);

        DailySchedule s1 = dailyScheduleRepository.save(
                new DailySchedule(member, d1));
        s1.markGenerated(5, 5);
        dailyScheduleRepository.save(s1);

        DailySchedule s2 = dailyScheduleRepository.save(
                new DailySchedule(member, d2));
        s2.markGenerated(4, 2);
        dailyScheduleRepository.save(s2);

        mockMvc.perform(get("/api/dashboard/heatmap")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("year", String.valueOf(year)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.year").value(year))
                .andExpect(jsonPath("$.data.days", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data.days[0].date").value(d1.toString()))
                .andExpect(jsonPath("$.data.days[0].achievementRate").value(100))
                .andExpect(jsonPath("$.data.days[1].date").value(d2.toString()))
                .andExpect(jsonPath("$.data.days[1].achievementRate").value(50));
    }

    @Test
    @DisplayName("GET /api/dashboard/statistics - 주간 통계와 카테고리 분배 반환")
    void statistics_weeklyBreakdown() throws Exception {
        Category category = categoryRepository.save(
                new Category(member, "프로그래밍", "#FF9800", null, 0));

        StudyMaterial material = materialRepository.save(new StudyMaterial(
                member, "JPA 책", MaterialType.BOOK, category, null,
                null, DeadlineMode.FREE));
        StudyUnit unit = unitRepository.save(new StudyUnit(
                material, "1장", 0, 60, null));

        LocalDate monday = LocalDate.now().with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        DailySchedule ds = dailyScheduleRepository.save(
                new DailySchedule(member, monday));
        ScheduleBlock block = new ScheduleBlock(ds, BlockType.STUDY,
                unit.getId(), LocalTime.of(9, 0), LocalTime.of(10, 0), 0);
        block.complete();
        scheduleBlockRepository.save(block);

        mockMvc.perform(get("/api/dashboard/statistics")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("period", "WEEKLY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("WEEKLY"))
                .andExpect(jsonPath("$.data.totalStudyMinutes").value(60))
                .andExpect(jsonPath("$.data.totalReviewMinutes").value(0))
                .andExpect(jsonPath("$.data.categoryBreakdown", hasSize(1)))
                .andExpect(jsonPath("$.data.categoryBreakdown[0].name")
                        .value("프로그래밍"))
                .andExpect(jsonPath("$.data.categoryBreakdown[0].minutes").value(60))
                .andExpect(jsonPath("$.data.categoryBreakdown[0].percent").value(100));
    }

    @Test
    @DisplayName("GET /api/dashboard/statistics - 기본값 WEEKLY로 동작")
    void statistics_defaultPeriodWeekly() throws Exception {
        mockMvc.perform(get("/api/dashboard/statistics")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("WEEKLY"))
                .andExpect(jsonPath("$.data.totalStudyMinutes").value(0));
    }
}
