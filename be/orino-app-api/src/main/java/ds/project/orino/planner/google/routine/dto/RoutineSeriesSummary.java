package ds.project.orino.planner.google.routine.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 루틴 시리즈 요약(생성 응답·목록 응답 공용). 마스터 이벤트 한 건을 표현한다.
 *
 * @param recurringEventId 마스터 시리즈 id(안정 식별자)
 * @param type             "habit" | "schedule"
 * @param allDay           종일 여부
 * @param start            종일이면 날짜, 시간 루틴이면 datetime
 * @param end              시간 루틴의 종료 datetime(종일이면 null)
 * @param recurrence       반복 규칙(정규화된 형태)
 * @param recurrenceText   한국어 표시 문구("매주 월·수·금")
 * @param color            표시 색상(null 가능)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoutineSeriesSummary(
        String recurringEventId,
        String type,
        String title,
        boolean allDay,
        String start,
        String end,
        RoutineRecurrence recurrence,
        String recurrenceText,
        String color
) {
}
