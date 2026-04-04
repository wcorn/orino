package ds.project.orino.planner.material.dto;

import ds.project.orino.domain.material.entity.MissedPolicy;
import ds.project.orino.domain.material.entity.ReviewConfig;

public record ReviewConfigResponse(
        Long materialId,
        String intervals,
        MissedPolicy missedPolicy
) {

    public static ReviewConfigResponse from(ReviewConfig rc) {
        return new ReviewConfigResponse(
                rc.getMaterial().getId(),
                rc.getIntervals(),
                rc.getMissedPolicy()
        );
    }
}
