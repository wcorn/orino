package ds.project.orino.planner.rescheduling.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RescheduleOption(
        RescheduleStrategy strategy,
        String label,
        String description,
        boolean deadlineFeasible,
        LocalDate newEstimatedCompletion,
        Integer dailyIncreasePercent,
        String warning) {
}
