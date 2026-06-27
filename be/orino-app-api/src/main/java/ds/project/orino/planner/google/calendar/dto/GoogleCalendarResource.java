package ds.project.orino.planner.google.calendar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Google Calendar calendars.insert 응답(필요한 필드만). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleCalendarResource(
        String id,
        String summary
) {
}
