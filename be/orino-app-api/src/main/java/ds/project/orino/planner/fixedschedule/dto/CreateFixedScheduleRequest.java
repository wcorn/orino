package ds.project.orino.planner.fixedschedule.dto;

import ds.project.orino.domain.fixedschedule.entity.RecurrenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateFixedScheduleRequest(
        @NotBlank @Size(max = 100) String title,
        Long categoryId,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        LocalDate scheduleDate,
        @NotNull RecurrenceType recurrenceType,
        Integer recurrenceInterval,
        String recurrenceDays,
        LocalDate recurrenceStart,
        LocalDate recurrenceEnd
) {
}
