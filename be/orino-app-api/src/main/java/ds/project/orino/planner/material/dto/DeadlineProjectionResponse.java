package ds.project.orino.planner.material.dto;

import ds.project.orino.domain.material.entity.PaceStatus;

import java.time.LocalDate;

public record DeadlineProjectionResponse(
        LocalDate deadline,
        int totalUnits,
        int completedUnits,
        int remainingUnits,
        int remainingMinutes,
        int availableDays,
        int requiredUnitsPerDay,
        int requiredMinutesPerDay,
        int allocatedMinutesPerDay,
        PaceStatus paceStatus
) {
}
