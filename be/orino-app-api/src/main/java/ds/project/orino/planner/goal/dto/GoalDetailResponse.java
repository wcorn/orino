package ds.project.orino.planner.goal.dto;

import ds.project.orino.domain.goal.entity.Goal;
import ds.project.orino.domain.goal.entity.GoalStatus;
import ds.project.orino.domain.goal.entity.PeriodType;

import java.time.LocalDate;
import java.util.List;

public record GoalDetailResponse(
        Long id,
        String title,
        String description,
        Long categoryId,
        PeriodType periodType,
        LocalDate startDate,
        LocalDate deadline,
        GoalStatus status,
        List<MilestoneResponse> milestones
) {

    public static GoalDetailResponse from(Goal goal) {
        List<MilestoneResponse> milestoneResponses = goal.getMilestones().stream()
                .map(MilestoneResponse::from)
                .toList();

        return new GoalDetailResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getDescription(),
                goal.getCategory() != null ? goal.getCategory().getId() : null,
                goal.getPeriodType(),
                goal.getStartDate(),
                goal.getDeadline(),
                goal.getStatus(),
                milestoneResponses
        );
    }
}
