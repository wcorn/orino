package ds.project.orino.planner.goal.dto;

import ds.project.orino.domain.goal.entity.Goal;
import ds.project.orino.domain.goal.entity.GoalStatus;
import ds.project.orino.domain.goal.entity.MilestoneStatus;
import ds.project.orino.domain.goal.entity.PeriodType;

import java.time.LocalDate;

public record GoalResponse(
        Long id,
        String title,
        String description,
        Long categoryId,
        PeriodType periodType,
        LocalDate startDate,
        LocalDate deadline,
        GoalStatus status,
        int milestoneCount,
        int completedMilestoneCount
) {

    public static GoalResponse from(Goal goal) {
        int total = goal.getMilestones().size();
        int completed = (int) goal.getMilestones().stream()
                .filter(m -> m.getStatus() == MilestoneStatus.COMPLETED)
                .count();

        return new GoalResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getDescription(),
                goal.getCategory() != null ? goal.getCategory().getId() : null,
                goal.getPeriodType(),
                goal.getStartDate(),
                goal.getDeadline(),
                goal.getStatus(),
                total,
                completed
        );
    }
}
