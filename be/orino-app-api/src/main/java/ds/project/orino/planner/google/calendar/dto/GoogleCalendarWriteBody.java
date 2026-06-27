package ds.project.orino.planner.google.calendar.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Google Calendar calendars.insert 요청 바디(보조 캘린더 생성). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoogleCalendarWriteBody(
        String summary
) {
}
