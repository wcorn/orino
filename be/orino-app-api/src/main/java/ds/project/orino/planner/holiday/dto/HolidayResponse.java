package ds.project.orino.planner.holiday.dto;

/**
 * 공휴일 응답 1건.
 *
 * @param date 공휴일 날짜("2026-06-06")
 * @param name 공휴일 이름("현충일")
 */
public record HolidayResponse(String date, String name) {
}
