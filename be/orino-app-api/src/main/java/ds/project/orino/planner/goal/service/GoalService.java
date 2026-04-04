package ds.project.orino.planner.goal.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.category.entity.Category;
import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.goal.entity.Goal;
import ds.project.orino.domain.goal.entity.GoalStatus;
import ds.project.orino.domain.goal.entity.Milestone;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class GoalService {

    private final GoalRepository goalRepository;
    private final MilestoneRepository milestoneRepository;
    private final MemberRepository memberRepository;
    private final CategoryRepository categoryRepository;

    public GoalService(GoalRepository goalRepository, MilestoneRepository milestoneRepository,
                       MemberRepository memberRepository, CategoryRepository categoryRepository) {
        this.goalRepository = goalRepository;
        this.milestoneRepository = milestoneRepository;
        this.memberRepository = memberRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<GoalResponse> getGoals(Long memberId) {
        return goalRepository.findByMemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(GoalResponse::from)
                .toList();
    }

    public GoalDetailResponse getGoal(Long memberId, Long goalId) {
        Goal goal = goalRepository.findByIdAndMemberId(goalId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        return GoalDetailResponse.from(goal);
    }

    @Transactional
    public GoalResponse create(Long memberId, CreateGoalRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        Category category = resolveCategory(request.categoryId(), memberId);

        Goal goal = new Goal(member, category, request.title(), request.description(),
                request.periodType(), request.startDate(), request.deadline());

        return GoalResponse.from(goalRepository.save(goal));
    }

    @Transactional
    public GoalResponse update(Long memberId, Long goalId, UpdateGoalRequest request) {
        Goal goal = goalRepository.findByIdAndMemberId(goalId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        Category category = resolveCategory(request.categoryId(), memberId);

        goal.update(category, request.title(), request.description(),
                request.periodType(), request.startDate(), request.deadline());

        return GoalResponse.from(goal);
    }

    @Transactional
    public void delete(Long memberId, Long goalId) {
        Goal goal = goalRepository.findByIdAndMemberId(goalId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        goalRepository.delete(goal);
    }

    @Transactional
    public GoalResponse changeStatus(Long memberId, Long goalId, GoalStatus status) {
        Goal goal = goalRepository.findByIdAndMemberId(goalId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        goal.changeStatus(status);

        return GoalResponse.from(goal);
    }

    @Transactional
    public MilestoneResponse createMilestone(Long memberId, Long goalId, CreateMilestoneRequest request) {
        Goal goal = goalRepository.findByIdAndMemberId(goalId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        Milestone milestone = new Milestone(goal, request.title(), request.deadline(), request.sortOrder());

        return MilestoneResponse.from(milestoneRepository.save(milestone));
    }

    @Transactional
    public MilestoneResponse updateMilestone(Long memberId, Long goalId, Long milestoneId,
                                             UpdateMilestoneRequest request) {
        goalRepository.findByIdAndMemberId(goalId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        Milestone milestone = milestoneRepository.findByIdAndGoalId(milestoneId, goalId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        milestone.update(request.title(), request.deadline(), request.sortOrder());

        return MilestoneResponse.from(milestone);
    }

    @Transactional
    public void deleteMilestone(Long memberId, Long goalId, Long milestoneId) {
        goalRepository.findByIdAndMemberId(goalId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        Milestone milestone = milestoneRepository.findByIdAndGoalId(milestoneId, goalId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        milestoneRepository.delete(milestone);
    }

    @Transactional
    public MilestoneResponse completeMilestone(Long memberId, Long goalId, Long milestoneId) {
        goalRepository.findByIdAndMemberId(goalId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        Milestone milestone = milestoneRepository.findByIdAndGoalId(milestoneId, goalId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        milestone.complete();

        return MilestoneResponse.from(milestone);
    }

    private Category resolveCategory(Long categoryId, Long memberId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findByIdAndMemberId(categoryId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
