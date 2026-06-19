package ds.project.orino.planner.google.calendar.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Google Calendar events.insert/patch 요청 바디(필요한 필드만). null 필드는 직렬화에서 제외한다.
 *
 * @param recurrence         RRULE 문자열 목록(예: {@code ["RRULE:FREQ=WEEKLY;BYDAY=MO,WE"]}). null이면 단일 일정
 * @param extendedProperties orino 태깅용 확장 속성(루틴 식별). null이면 미설정
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoogleEventWriteBody(
        String summary,
        String location,
        String description,
        GoogleEventTime start,
        GoogleEventTime end,
        List<String> recurrence,
        GoogleExtendedProperties extendedProperties
) {

    /** 반복·태깅 없는 단일 일정 바디. */
    public GoogleEventWriteBody(String summary, String location, String description,
                               GoogleEventTime start, GoogleEventTime end) {
        this(summary, location, description, start, end, null, null);
    }

    /** 시간 일정이면 dateTime+timeZone, 종일이면 date. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GoogleEventTime(String dateTime, String date, String timeZone) {
    }
}
