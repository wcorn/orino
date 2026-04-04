package ds.project.orino.planner.material.dto;

import ds.project.orino.domain.material.entity.MaterialDailyOverride;

import java.time.LocalDate;

public record DailyOverrideResponse(
        Long id,
        LocalDate overrideDate,
        int minutes
) {

    public static DailyOverrideResponse from(MaterialDailyOverride o) {
        return new DailyOverrideResponse(
                o.getId(),
                o.getOverrideDate(),
                o.getMinutes()
        );
    }
}
