package ds.project.orino.planner.scheduling.dirty;

import ds.project.orino.domain.calendar.entity.DailySchedule;
import ds.project.orino.domain.calendar.repository.DailyScheduleRepository;
import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.fixedschedule.repository.FixedScheduleRepository;
import ds.project.orino.domain.goal.repository.GoalRepository;
import ds.project.orino.domain.goal.repository.MilestoneRepository;
import ds.project.orino.domain.material.repository.MaterialAllocationRepository;
import ds.project.orino.domain.material.repository.MaterialDailyOverrideRepository;
import ds.project.orino.domain.material.repository.ReviewConfigRepository;
import ds.project.orino.domain.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.material.repository.StudyUnitRepository;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.preference.repository.PriorityRuleRepository;
import ds.project.orino.domain.preference.repository.UserPreferenceRepository;
import ds.project.orino.domain.review.repository.ReviewScheduleRepository;
import ds.project.orino.domain.routine.repository.RoutineCheckRepository;
import ds.project.orino.domain.routine.repository.RoutineExceptionRepository;
import ds.project.orino.domain.routine.repository.RoutineRepository;
import ds.project.orino.domain.todo.repository.TodoRepository;
import ds.project.orino.support.IntegrationTest;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class DirtyScheduleMarkerIntegrationTest {

    @Autowired private DirtyScheduleMarker marker;
    @Autowired private DailyScheduleRepository dailyScheduleRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private UserPreferenceRepository userPreferenceRepository;
    @Autowired private PriorityRuleRepository priorityRuleRepository;
    @Autowired private ReviewScheduleRepository reviewScheduleRepository;
    @Autowired private ReviewConfigRepository reviewConfigRepository;
    @Autowired private MaterialDailyOverrideRepository dailyOverrideRepository;
    @Autowired private MaterialAllocationRepository allocationRepository;
    @Autowired private StudyUnitRepository unitRepository;
    @Autowired private StudyMaterialRepository materialRepository;
    @Autowired private RoutineExceptionRepository routineExceptionRepository;
    @Autowired private RoutineCheckRepository routineCheckRepository;
    @Autowired private RoutineRepository routineRepository;
    @Autowired private FixedScheduleRepository fixedScheduleRepository;
    @Autowired private TodoRepository todoRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Member member;
    private LocalDate today;

    @BeforeEach
    void setUp() {
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
        today = LocalDate.now();
    }

    @Test
    @DisplayName("markDirtyFromToday - 오늘 이후의 모든 스케줄을 dirty=true로 변경한다")
    void markDirtyFromToday_updatesFutureSchedules() {
        DailySchedule past = saveCleanSchedule(today.minusDays(1));
        DailySchedule todaySchedule = saveCleanSchedule(today);
        DailySchedule future = saveCleanSchedule(today.plusDays(5));

        marker.markDirtyFromToday(member.getId());

        assertThat(reload(past).isDirty()).isFalse();
        assertThat(reload(todaySchedule).isDirty()).isTrue();
        assertThat(reload(future).isDirty()).isTrue();
    }

    @Test
    @DisplayName("markDirtyOn - 지정 날짜의 스케줄만 dirty=true로 변경한다")
    void markDirtyOn_updatesSingleDate() {
        DailySchedule day1 = saveCleanSchedule(today);
        DailySchedule day2 = saveCleanSchedule(today.plusDays(1));

        marker.markDirtyOn(member.getId(), today.plusDays(1));

        assertThat(reload(day1).isDirty()).isFalse();
        assertThat(reload(day2).isDirty()).isTrue();
    }

    @Test
    @DisplayName("다른 사용자의 스케줄은 영향을 받지 않는다")
    void doesNotAffectOtherMembers() {
        Member other = memberRepository.save(new Member("other", "pw"));
        DailySchedule mine = saveCleanSchedule(today);
        DailySchedule theirs = saveCleanScheduleFor(other, today);

        marker.markDirtyFromToday(member.getId());

        assertThat(reload(mine).isDirty()).isTrue();
        assertThat(reload(theirs).isDirty()).isFalse();
    }

    private DailySchedule saveCleanSchedule(LocalDate date) {
        return saveCleanScheduleFor(member, date);
    }

    private DailySchedule saveCleanScheduleFor(Member m, LocalDate date) {
        DailySchedule schedule = new DailySchedule(m, date);
        schedule.markGenerated(0, 0);
        return dailyScheduleRepository.save(schedule);
    }

    private DailySchedule reload(DailySchedule schedule) {
        return dailyScheduleRepository.findById(schedule.getId()).orElseThrow();
    }
}
