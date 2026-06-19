package ds.project.orino.planner.google.calendar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Google Calendar events.list 응답(필요한 필드만). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleEventsResponse(List<GoogleEventItem> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoogleEventItem(
            String id,
            String summary,
            String location,
            String recurringEventId,
            GoogleEventDateTime start,
            GoogleEventDateTime end,
            List<String> recurrence,
            GoogleExtendedProperties extendedProperties
    ) {
    }

    /** start/end는 시간 일정이면 dateTime(RFC3339), 종일이면 date("yyyy-MM-dd"). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoogleEventDateTime(String dateTime, String date) {
    }
}
