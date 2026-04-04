package ds.project.orino.planner.goal.dto;

import ds.project.orino.domain.goal.entity.GoalStatus;
import jakarta.validation.constraints.NotNull;

public record GoalStatusRequest(
        @NotNull GoalStatus status
) {
}
