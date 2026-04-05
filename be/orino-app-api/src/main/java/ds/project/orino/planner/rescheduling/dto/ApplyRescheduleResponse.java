package ds.project.orino.planner.rescheduling.dto;

public record ApplyRescheduleResponse(
        RescheduleStrategy strategy,
        int affectedDays,
        int newDailyStudyMinutes) {
}
