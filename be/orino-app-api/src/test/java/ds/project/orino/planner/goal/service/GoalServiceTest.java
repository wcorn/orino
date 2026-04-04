package ds.project.orino.planner.goal.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.category.entity.Category;
import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.goal.entity.Goal;
import ds.project.orino.domain.goal.entity.GoalStatus;
import ds.project.orino.domain.goal.entity.Milestone;
import ds.project.orino.domain.goal.entity.MilestoneStatus;
import ds.project.orino.domain.goal.entity.PeriodType;
import ds.project.orino.domain.goal.repository.GoalRepository;
import ds.project.orino.domain.goal.repository.MilestoneRepository;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.planner.goal.dto.CreateGoalRequest;
import ds.project.orino.planner.goal.dto.CreateMilestoneRequest;
import ds.project.orino.planner.goal.dto.GoalDetailResponse;
import ds.project.orino.planner.goal.dto.GoalResponse;
import ds.project.orino.planner.goal.dto.MilestoneResponse;
import ds.project.orino.planner.goal.dto.UpdateGoalRequest;
import ds.project.orino.planner.goal.dto.UpdateMilestoneRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    private GoalService goalService;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private Member member;

    @BeforeEach
    void setUp() {
        goalService = new GoalService(goalRepository, milestoneRepository, memberRepository, categoryRepository);
        member = new Member("admin", "encoded");
    }

    @Test
    @DisplayName("목표 목록을 조회한다")
    void getGoals() {
        Goal goal = new Goal(member, null, "목표1", null, PeriodType.QUARTER,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));

        given(goalRepository.findByMemberIdOrderByCreatedAtDesc(1L))
                .willReturn(List.of(goal));

        List<GoalResponse> result = goalService.getGoals(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("목표1");
    }

    @Test
    @DisplayName("목표 상세를 조회한다")
    void getGoal() {
        Goal goal = new Goal(member, null, "목표1", "설명", PeriodType.YEAR,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        given(goalRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(goal));

        GoalDetailResponse result = goalService.getGoal(1L, 1L);

        assertThat(result.title()).isEqualTo("목표1");
        assertThat(result.description()).isEqualTo("설명");
    }

    @Test
    @DisplayName("목표를 생성한다")
    void create() {
        Goal saved = new Goal(member, null, "새 목표", null, PeriodType.QUARTER,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(goalRepository.save(any(Goal.class))).willReturn(saved);

        GoalResponse result = goalService.create(1L,
                new CreateGoalRequest("새 목표", null, null, PeriodType.QUARTER,
                        LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30)));

        assertThat(result.title()).isEqualTo("새 목표");
        assertThat(result.periodType()).isEqualTo(PeriodType.QUARTER);
    }

    @Test
    @DisplayName("카테고리를 지정하여 목표를 생성한다")
    void create_withCategory() {
        Category category = new Category(member, "프로그래밍", "#FF9800", "code", 0);
        Goal saved = new Goal(member, category, "새 목표", null, PeriodType.QUARTER,
                LocalDate.of(2026, 4, 1), null);

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(categoryRepository.findByIdAndMemberId(1L, 1L)).willReturn(Optional.of(category));
        given(goalRepository.save(any(Goal.class))).willReturn(saved);

        GoalResponse result = goalService.create(1L,
                new CreateGoalRequest("새 목표", null, 1L, PeriodType.QUARTER,
                        LocalDate.of(2026, 4, 1), null));

        assertThat(result.title()).isEqualTo("새 목표");
        verify(categoryRepository).findByIdAndMemberId(1L, 1L);
    }

    @Test
    @DisplayName("존재하지 않는 회원으로 목표 생성 시 예외를 던진다")
    void create_memberNotFound() {
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.create(999L,
                new CreateGoalRequest("테스트", null, null, PeriodType.QUARTER,
                        LocalDate.of(2026, 4, 1), null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("목표를 수정한다")
    void update() {
        Goal goal = new Goal(member, null, "기존 목표", null, PeriodType.QUARTER,
                LocalDate.of(2026, 4, 1), null);

        given(goalRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(goal));

        GoalResponse result = goalService.update(1L, 1L,
                new UpdateGoalRequest("수정된 목표", "새 설명", null, PeriodType.YEAR,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));

        assertThat(result.title()).isEqualTo("수정된 목표");
        assertThat(result.periodType()).isEqualTo(PeriodType.YEAR);
    }

    @Test
    @DisplayName("존재하지 않는 목표 수정 시 예외를 던진다")
    void update_notFound() {
        given(goalRepository.findByIdAndMemberId(999L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.update(1L, 999L,
                new UpdateGoalRequest("이름", null, null, PeriodType.QUARTER,
                        LocalDate.of(2026, 4, 1), null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("목표를 삭제한다")
    void delete() {
        Goal goal = new Goal(member, null, "삭제대상", null, PeriodType.QUARTER,
                LocalDate.of(2026, 4, 1), null);

        given(goalRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(goal));

        goalService.delete(1L, 1L);

        verify(goalRepository).delete(goal);
    }

    @Test
    @DisplayName("존재하지 않는 목표 삭제 시 예외를 던진다")
    void delete_notFound() {
        given(goalRepository.findByIdAndMemberId(999L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.delete(1L, 999L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("목표 상태를 변경한다")
    void changeStatus() {
        Goal goal = new Goal(member, null, "목표", null, PeriodType.QUARTER,
                LocalDate.of(2026, 4, 1), null);

        given(goalRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(goal));

        GoalResponse result = goalService.changeStatus(1L, 1L, GoalStatus.COMPLETED);

        assertThat(result.status()).isEqualTo(GoalStatus.COMPLETED);
    }

    @Test
    @DisplayName("마일스톤을 추가한다")
    void createMilestone() {
        Goal goal = new Goal(member, null, "목표", null, PeriodType.QUARTER,
                LocalDate.of(2026, 4, 1), null);
        Milestone saved = new Milestone(goal, "마일스톤1", LocalDate.of(2026, 5, 1), 0);

        given(goalRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(goal));
        given(milestoneRepository.save(any(Milestone.class))).willReturn(saved);

        MilestoneResponse result = goalService.createMilestone(1L, 1L,
                new CreateMilestoneRequest("마일스톤1", LocalDate.of(2026, 5, 1), 0));

        assertThat(result.title()).isEqualTo("마일스톤1");
        assertThat(result.status()).isEqualTo(MilestoneStatus.PENDING);
    }

    @Test
    @DisplayName("마일스톤을 수정한다")
    void updateMilestone() {
        Goal goal = new Goal(member, null, "목표", null, PeriodType.QUARTER,
                LocalDate.of(2026, 4, 1), null);
        Milestone milestone = new Milestone(goal, "기존", null, 0);

        given(goalRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(goal));
        given(milestoneRepository.findByIdAndGoalId(1L, 1L))
                .willReturn(Optional.of(milestone));

        MilestoneResponse result = goalService.updateMilestone(1L, 1L, 1L,
                new UpdateMilestoneRequest("수정됨", LocalDate.of(2026, 5, 15), 2));

        assertThat(result.title()).isEqualTo("수정됨");
        assertThat(result.sortOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("마일스톤을 삭제한다")
    void deleteMilestone() {
        Goal goal = new Goal(member, null, "목표", null, PeriodType.QUARTER,
                LocalDate.of(2026, 4, 1), null);
        Milestone milestone = new Milestone(goal, "삭제대상", null, 0);

        given(goalRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(goal));
        given(milestoneRepository.findByIdAndGoalId(1L, 1L))
                .willReturn(Optional.of(milestone));

        goalService.deleteMilestone(1L, 1L, 1L);

        verify(milestoneRepository).delete(milestone);
    }

    @Test
    @DisplayName("마일스톤을 완료 처리한다")
    void completeMilestone() {
        Goal goal = new Goal(member, null, "목표", null, PeriodType.QUARTER,
                LocalDate.of(2026, 4, 1), null);
        Milestone milestone = new Milestone(goal, "마일스톤", null, 0);

        given(goalRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(goal));
        given(milestoneRepository.findByIdAndGoalId(1L, 1L))
                .willReturn(Optional.of(milestone));

        MilestoneResponse result = goalService.completeMilestone(1L, 1L, 1L);

        assertThat(result.status()).isEqualTo(MilestoneStatus.COMPLETED);
    }

    @Test
    @DisplayName("존재하지 않는 목표의 마일스톤 추가 시 예외를 던진다")
    void createMilestone_goalNotFound() {
        given(goalRepository.findByIdAndMemberId(999L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.createMilestone(1L, 999L,
                new CreateMilestoneRequest("마일스톤", null, 0)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
