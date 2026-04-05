package ds.project.orino.planner.scheduling.engine;

import ds.project.orino.domain.calendar.entity.BlockStatus;
import ds.project.orino.domain.calendar.entity.BlockType;
import ds.project.orino.domain.calendar.entity.DailySchedule;
import ds.project.orino.domain.calendar.entity.ScheduleBlock;
import ds.project.orino.domain.calendar.repository.DailyScheduleRepository;
import ds.project.orino.domain.calendar.repository.ScheduleBlockRepository;
import ds.project.orino.domain.fixedschedule.entity.FixedSchedule;
import ds.project.orino.domain.fixedschedule.entity.RecurrenceType;
import ds.project.orino.domain.fixedschedule.repository.FixedScheduleRepository;
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
import ds.project.orino.domain.preference.entity.StudyTimePreference;
import ds.project.orino.domain.preference.entity.UserPreference;
import ds.project.orino.domain.preference.repository.PriorityRuleRepository;
import ds.project.orino.domain.preference.repository.UserPreferenceRepository;
import ds.project.orino.domain.review.entity.ReviewSchedule;
import ds.project.orino.domain.review.repository.ReviewScheduleRepository;
import ds.project.orino.domain.routine.entity.Routine;
import ds.project.orino.domain.routine.repository.RoutineCheckRepository;
import ds.project.orino.domain.routine.repository.RoutineExceptionRepository;
import ds.project.orino.domain.routine.repository.RoutineRepository;
import ds.project.orino.domain.todo.entity.Priority;
import ds.project.orino.domain.todo.entity.Todo;
import ds.project.orino.domain.todo.repository.TodoRepository;
import ds.project.orino.domain.goal.repository.GoalRepository;
import ds.project.orino.domain.goal.repository.MilestoneRepository;
import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.planner.scheduling.dirty.DirtyScheduleMarker;
import ds.project.orino.planner.scheduling.engine.model.SchedulingResult;
import ds.project.orino.support.IntegrationTest;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class SchedulingEngineIntegrationTest {

    @Autowired private SchedulingEngine engine;
    @Autowired private DirtyScheduleMarker dirtyScheduleMarker;
    @Autowired private MemberRepository memberRepository;
    @Autowired private UserPreferenceRepository preferenceRepository;
    @Autowired private PriorityRuleRepository priorityRuleRepository;
    @Autowired private FixedScheduleRepository fixedScheduleRepository;
    @Autowired private RoutineRepository routineRepository;
    @Autowired private RoutineCheckRepository routineCheckRepository;
    @Autowired private RoutineExceptionRepository routineExceptionRepository;
    @Autowired private TodoRepository todoRepository;
    @Autowired private StudyMaterialRepository materialRepository;
    @Autowired private StudyUnitRepository unitRepository;
    @Autowired private MaterialAllocationRepository allocationRepository;
    @Autowired private MaterialDailyOverrideRepository overrideRepository;
    @Autowired private ReviewConfigRepository reviewConfigRepository;
    @Autowired private ReviewScheduleRepository reviewRepository;
    @Autowired private DailyScheduleRepository dailyScheduleRepository;
    @Autowired private ScheduleBlockRepository scheduleBlockRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Member member;
    private LocalDate targetDate;

    @BeforeEach
    void setUp() {
        dailyScheduleRepository.deleteAll();
        reviewRepository.deleteAll();
        priorityRuleRepository.deleteAll();
        preferenceRepository.deleteAll();
        reviewConfigRepository.deleteAll();
        overrideRepository.deleteAll();
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
    }

    @AfterEach
    void tearDown() {
        dailyScheduleRepository.deleteAll();
        reviewRepository.deleteAll();
    }

    @Test
    @DisplayName("학습 단위 3개를 자유시간에 순서대로 배치한다")
    void placesStudyUnits() {
        saveDefaultPreference();

        StudyMaterial material = materialRepository.save(new StudyMaterial(
                member, "알고리즘", MaterialType.BOOK, null, null,
                null, DeadlineMode.FREE));
        unitRepository.save(new StudyUnit(material, "챕터1", 1, 30, null));
        unitRepository.save(new StudyUnit(material, "챕터2", 2, 30, null));
        unitRepository.save(new StudyUnit(material, "챕터3", 3, 30, null));

        SchedulingResult result = engine.generate(member.getId(), targetDate);

        DailySchedule schedule = result.dailySchedule();
        assertThat(schedule.getBlocks()).hasSize(3);
        assertThat(schedule.getBlocks()).allMatch(
                b -> b.getBlockType() == BlockType.STUDY);
        assertThat(schedule.getBlocks().get(0).getStartTime())
                .isEqualTo(LocalTime.of(7, 0));
    }

    @Test
    @DisplayName("고정 일정은 자유시간에서 제외되고 preplaced로 배치된다")
    void fixedScheduleBlocksTime() {
        saveDefaultPreference();

        fixedScheduleRepository.save(new FixedSchedule(
                member, "영어회화", null,
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                null, RecurrenceType.DAILY,
                null, null, targetDate.minusDays(1), null));

        StudyMaterial material = materialRepository.save(new StudyMaterial(
                member, "수학", MaterialType.BOOK, null, null,
                null, DeadlineMode.FREE));
        unitRepository.save(new StudyUnit(material, "단위1", 1, 30, null));

        SchedulingResult result = engine.generate(member.getId(), targetDate);

        List<ScheduleBlock> blocks = result.dailySchedule().getBlocks()
                .stream().sorted((a, b) -> a.getStartTime()
                        .compareTo(b.getStartTime())).toList();
        assertThat(blocks).hasSizeGreaterThanOrEqualTo(2);
        ScheduleBlock fixed = blocks.stream()
                .filter(b -> b.getBlockType() == BlockType.FIXED)
                .findFirst().orElseThrow();
        assertThat(fixed.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(fixed.getEndTime()).isEqualTo(LocalTime.of(10, 0));

        // 학습 블록은 9~10시 범위와 겹치지 않아야 함
        ScheduleBlock study = blocks.stream()
                .filter(b -> b.getBlockType() == BlockType.STUDY)
                .findFirst().orElseThrow();
        boolean overlap = !(study.getEndTime().isBefore(
                LocalTime.of(9, 0))
                || study.getStartTime().isAfter(LocalTime.of(10, 0)));
        if (overlap) {
            assertThat(study.getEndTime())
                    .isBeforeOrEqualTo(LocalTime.of(9, 0));
        }
    }

    @Test
    @DisplayName("밀린 복습이 최우선으로 배치된다")
    void overdueReviewFirst() {
        saveDefaultPreference();

        StudyMaterial material = materialRepository.save(new StudyMaterial(
                member, "역사", MaterialType.BOOK, null, null,
                null, DeadlineMode.FREE));
        StudyUnit unit = unitRepository.save(
                new StudyUnit(material, "단원1", 1, 30, null));
        unitRepository.save(new StudyUnit(material, "단원2", 2, 30, null));

        reviewRepository.save(new ReviewSchedule(
                unit, 1, targetDate.minusDays(2)));

        SchedulingResult result = engine.generate(member.getId(), targetDate);

        ScheduleBlock first = result.dailySchedule().getBlocks().stream()
                .filter(b -> !b.isPinned() && b.getStatus() != BlockStatus.COMPLETED)
                .min((a, b) -> a.getStartTime().compareTo(b.getStartTime()))
                .orElseThrow();
        assertThat(first.getBlockType()).isEqualTo(BlockType.REVIEW);
    }

    @Test
    @DisplayName("학습 자료 시간 할당을 초과한 분량은 이월된다")
    void materialAllocationLimit() {
        saveDefaultPreference();

        StudyMaterial material = materialRepository.save(new StudyMaterial(
                member, "영어", MaterialType.BOOK, null, null,
                null, DeadlineMode.FREE));
        unitRepository.save(new StudyUnit(material, "챕터1", 1, 60, null));
        unitRepository.save(new StudyUnit(material, "챕터2", 2, 60, null));
        unitRepository.save(new StudyUnit(material, "챕터3", 3, 60, null));
        allocationRepository.save(new MaterialAllocation(material, 60));

        SchedulingResult result = engine.generate(member.getId(), targetDate);

        int studyMinutes = result.dailySchedule().getBlocks().stream()
                .filter(b -> b.getBlockType() == BlockType.STUDY)
                .mapToInt(b -> (int) java.time.Duration.between(
                        b.getStartTime(), b.getEndTime()).toMinutes())
                .sum();
        assertThat(studyMinutes).isEqualTo(60);
        assertThat(result.warnings()).anyMatch(w -> w.message()
                .contains("이월"));
    }

    @Test
    @DisplayName("과거 날짜는 재생성하지 않는다")
    void pastDateNotRegenerated() {
        saveDefaultPreference();
        LocalDate pastDate = LocalDate.now().minusDays(3);

        DailySchedule existing = dailyScheduleRepository.save(
                new DailySchedule(member, pastDate));

        SchedulingResult result = engine.generate(member.getId(), pastDate);

        assertThat(result.dailySchedule().getId()).isEqualTo(existing.getId());
        assertThat(result.dailySchedule().getBlocks()).isEmpty();
    }

    @Test
    @DisplayName("할 일의 마감 임박 여부에 따라 우선순위가 달라진다")
    void urgentTodoPriority() {
        saveDefaultPreference();

        Todo urgent = todoRepository.save(new Todo(
                member, "urgent", null, null, null, Priority.HIGH,
                targetDate.plusDays(1), 30));
        Todo noDeadline = todoRepository.save(new Todo(
                member, "noDeadline", null, null, null, Priority.LOW,
                null, 30));

        SchedulingResult result = engine.generate(member.getId(), targetDate);

        List<ScheduleBlock> sorted = result.dailySchedule().getBlocks().stream()
                .filter(b -> b.getBlockType() == BlockType.TODO)
                .sorted((a, b) -> a.getStartTime().compareTo(b.getStartTime()))
                .toList();
        assertThat(sorted).hasSize(2);
        assertThat(sorted.get(0).getReferenceId()).isEqualTo(urgent.getId());
        assertThat(sorted.get(1).getReferenceId())
                .isEqualTo(noDeadline.getId());
    }

    @Test
    @DisplayName("dirty=false 인 스케줄은 재생성하지 않고 기존 블록을 반환한다")
    void cleanScheduleIsNotRegenerated() {
        saveDefaultPreference();

        StudyMaterial material = materialRepository.save(new StudyMaterial(
                member, "알고리즘", MaterialType.BOOK, null, null,
                null, DeadlineMode.FREE));
        unitRepository.save(new StudyUnit(material, "챕터1", 1, 30, null));

        SchedulingResult first = engine.generate(member.getId(), targetDate);
        int firstBlockCount = first.dailySchedule().getBlocks().size();
        assertThat(first.dailySchedule().isDirty()).isFalse();

        // 데이터 변경 후 dirty 마킹은 하지 않음
        unitRepository.save(new StudyUnit(material, "챕터2", 2, 30, null));

        // 재호출 시 dirty=false 이므로 재생성하지 않음
        SchedulingResult second = engine.generate(member.getId(), targetDate);
        assertThat(second.dailySchedule().getBlocks()).hasSize(firstBlockCount);
    }

    @Test
    @DisplayName("dirty=true 마킹하면 다음 호출에서 재생성한다")
    void dirtyScheduleIsRegenerated() {
        saveDefaultPreference();

        StudyMaterial material = materialRepository.save(new StudyMaterial(
                member, "알고리즘", MaterialType.BOOK, null, null,
                null, DeadlineMode.FREE));
        unitRepository.save(new StudyUnit(material, "챕터1", 1, 30, null));

        SchedulingResult first = engine.generate(member.getId(), targetDate);
        int firstBlockCount = first.dailySchedule().getBlocks().size();

        unitRepository.save(new StudyUnit(material, "챕터2", 2, 30, null));
        dirtyScheduleMarker.markDirtyFromToday(member.getId());

        SchedulingResult second = engine.generate(member.getId(), targetDate);
        assertThat(second.dailySchedule().getBlocks())
                .hasSize(firstBlockCount + 1);
    }

    @Test
    @DisplayName("완료/pinned 블록은 dirty 재생성 시에도 유지된다")
    void lockedBlocksPreservedOnRegeneration() {
        saveDefaultPreference();

        StudyMaterial material = materialRepository.save(new StudyMaterial(
                member, "알고리즘", MaterialType.BOOK, null, null,
                null, DeadlineMode.FREE));
        unitRepository.save(new StudyUnit(material, "챕터1", 1, 30, null));

        SchedulingResult first = engine.generate(member.getId(), targetDate);
        ScheduleBlock firstBlock = first.dailySchedule().getBlocks().get(0);
        Long completedBlockId = firstBlock.getId();
        LocalTime originalStart = firstBlock.getStartTime();
        // DB에 COMPLETED 상태를 반영
        ScheduleBlock managed = scheduleBlockRepository
                .findById(completedBlockId).orElseThrow();
        managed.complete();
        scheduleBlockRepository.save(managed);

        unitRepository.save(new StudyUnit(material, "챕터2", 2, 30, null));
        dirtyScheduleMarker.markDirtyFromToday(member.getId());

        SchedulingResult second = engine.generate(member.getId(), targetDate);
        ScheduleBlock preserved = second.dailySchedule().getBlocks().stream()
                .filter(b -> b.getId().equals(completedBlockId))
                .findFirst().orElseThrow();
        assertThat(preserved.getStatus()).isEqualTo(BlockStatus.COMPLETED);
        assertThat(preserved.getStartTime()).isEqualTo(originalStart);
    }

    private UserPreference saveDefaultPreference() {
        UserPreference preference = new UserPreference(member);
        ReflectionTestUtils.setField(preference, "wakeTime",
                LocalTime.of(7, 0));
        ReflectionTestUtils.setField(preference, "sleepTime",
                LocalTime.of(23, 0));
        ReflectionTestUtils.setField(preference, "focusMinutes", 50);
        ReflectionTestUtils.setField(preference, "breakMinutes", 10);
        ReflectionTestUtils.setField(preference, "studyTimePreference",
                StudyTimePreference.MORNING);
        return preferenceRepository.save(preference);
    }

    @SuppressWarnings("unused")
    private Routine anyRoutine() {
        return routineRepository.findAll().stream().findFirst().orElse(null);
    }
}
