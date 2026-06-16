package ds.project.orino.planner.google.calendar.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Google Calendar events.insert/patch 요청 바디(필요한 필드만). null 필드는 직렬화에서 제외한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoogleEventWriteBody(
        String summary,
        String location,
        String description,
        GoogleEventTime start,
        GoogleEventTime end
) {

    /** 시간 일정이면 dateTime+timeZone, 종일이면 date. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GoogleEventTime(String dateTime, String date, String timeZone) {
    }
}
