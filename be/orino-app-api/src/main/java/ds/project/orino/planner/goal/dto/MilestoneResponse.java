package ds.project.orino.planner.goal.dto;

import ds.project.orino.domain.goal.entity.Milestone;
import ds.project.orino.domain.goal.entity.MilestoneStatus;

import java.time.LocalDate;

public record MilestoneResponse(
        Long id,
        String title,
        LocalDate deadline,
        MilestoneStatus status,
        int sortOrder
) {

    public static MilestoneResponse from(Milestone milestone) {
        return new MilestoneResponse(
                milestone.getId(),
                milestone.getTitle(),
                milestone.getDeadline(),
                milestone.getStatus(),
                milestone.getSortOrder()
        );
    }
}
