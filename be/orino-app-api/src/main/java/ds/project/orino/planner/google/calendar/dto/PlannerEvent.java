package ds.project.orino.planner.google.calendar.dto;

/**
 * 통합 캘린더 피드의 일정(event). Google 응답을 사용자 시간대로 정규화한 형태.
 *
 * @param id        Google event id
 * @param title     제목(summary, null 가능)
 * @param allDay    종일 일정 여부
 * @param start     종일이면 날짜("2026-06-10"), 시간 일정이면 사용자 TZ 로컬 datetime("2026-06-10T14:00:00")
 * @param end       종료(종일은 포함 마지막 날짜로 보정)
 * @param location  장소(null 가능)
 * @param recurring 반복 일정의 인스턴스 여부
 * @param source    항상 "google"
 */
public record PlannerEvent(
        String id,
        String title,
        boolean allDay,
        String start,
        String end,
        String location,
        boolean recurring,
        String source
) {
}
