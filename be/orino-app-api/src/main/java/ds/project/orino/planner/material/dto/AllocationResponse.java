package ds.project.orino.planner.material.dto;

import ds.project.orino.domain.material.entity.MaterialAllocation;

public record AllocationResponse(
        Long materialId,
        int defaultMinutes
) {

    public static AllocationResponse from(MaterialAllocation a) {
        return new AllocationResponse(
                a.getMaterial().getId(),
                a.getDefaultMinutes()
        );
    }
}
