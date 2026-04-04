package ds.project.orino.planner.routine.dto;

import ds.project.orino.domain.fixedschedule.entity.RecurrenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateRoutineRequest(
        @NotBlank @Size(max = 100) String title,
        Long categoryId,
        @NotNull @Positive Integer durationMinutes,
        LocalTime preferredTime,
        @NotNull RecurrenceType recurrenceType,
        Integer recurrenceInterval,
        String recurrenceDays,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        Boolean skipHolidays
) {
}
