package ds.project.orino.planner.goal.dto;

import ds.project.orino.domain.planner.goal.entity.MonthlyGoal;

import java.time.Instant;

public record MonthlyGoalResponse(
        int year,
        int month,
        String content,
        Instant updatedAt
) {
    public static MonthlyGoalResponse of(MonthlyGoal goal) {
        return new MonthlyGoalResponse(
                goal.getYear(), goal.getMonth(), goal.getContent(), goal.getUpdatedAt());
    }
}
