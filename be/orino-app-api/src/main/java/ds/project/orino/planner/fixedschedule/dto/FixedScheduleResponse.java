package ds.project.orino.planner.fixedschedule.dto;

import ds.project.orino.domain.fixedschedule.entity.FixedSchedule;
import ds.project.orino.domain.fixedschedule.entity.RecurrenceType;

import java.time.LocalDate;
import java.time.LocalTime;

public record FixedScheduleResponse(
        Long id,
        String title,
        Long categoryId,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate scheduleDate,
        RecurrenceType recurrenceType,
        Integer recurrenceInterval,
        String recurrenceDays,
        LocalDate recurrenceStart,
        LocalDate recurrenceEnd
) {

    public static FixedScheduleResponse from(FixedSchedule schedule) {
        return new FixedScheduleResponse(
                schedule.getId(),
                schedule.getTitle(),
                schedule.getCategory() != null
                        ? schedule.getCategory().getId() : null,
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getScheduleDate(),
                schedule.getRecurrenceType(),
                schedule.getRecurrenceInterval(),
                schedule.getRecurrenceDays(),
                schedule.getRecurrenceStart(),
                schedule.getRecurrenceEnd()
        );
    }
}
