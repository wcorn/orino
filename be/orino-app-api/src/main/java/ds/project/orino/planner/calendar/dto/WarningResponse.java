package ds.project.orino.planner.calendar.dto;

import ds.project.orino.planner.scheduling.engine.model.SchedulingWarning;

public record WarningResponse(String type, String message) {

    public static WarningResponse from(SchedulingWarning warning) {
        return new WarningResponse(warning.type().name(), warning.message());
    }
}
