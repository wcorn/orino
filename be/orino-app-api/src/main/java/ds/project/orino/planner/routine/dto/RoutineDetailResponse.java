package ds.project.orino.planner.routine.dto;

import ds.project.orino.domain.fixedschedule.entity.RecurrenceType;
import ds.project.orino.domain.routine.entity.Routine;
import ds.project.orino.domain.routine.entity.RoutineStatus;

import java.time.LocalDate;
import java.time.LocalTime;

public record RoutineDetailResponse(
        Long id,
        String title,
        Long categoryId,
        int durationMinutes,
        LocalTime preferredTime,
        RecurrenceType recurrenceType,
        Integer recurrenceInterval,
        String recurrenceDays,
        LocalDate startDate,
        LocalDate endDate,
        boolean skipHolidays,
        RoutineStatus status,
        StreakInfo streak
) {

    public static RoutineDetailResponse from(Routine routine,
                                             StreakInfo streak) {
        return new RoutineDetailResponse(
                routine.getId(),
                routine.getTitle(),
                routine.getCategory() != null
                        ? routine.getCategory().getId() : null,
                routine.getDurationMinutes(),
                routine.getPreferredTime(),
                routine.getRecurrenceType(),
                routine.getRecurrenceInterval(),
                routine.getRecurrenceDays(),
                routine.getStartDate(),
                routine.getEndDate(),
                routine.isSkipHolidays(),
                routine.getStatus(),
                streak
        );
    }
}
