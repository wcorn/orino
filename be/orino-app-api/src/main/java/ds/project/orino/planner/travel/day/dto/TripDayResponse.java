package ds.project.orino.planner.travel.day.dto;

import java.time.LocalDate;

/**
 * 날짜 하나. 보드 응답에도 같은 내용이 들어가지만, 구간 편집기(S-03)와 담기 시트처럼
 * <b>일정 없이 날짜만</b> 필요한 화면이 따로 쓴다.
 *
 * @param dayIndex     1부터 시작하는 일차
 * @param weekday      한국어 요일 한 글자("화")
 * @param legIndex     이 날짜가 속한 구간 번호(1부터). 저장된 값이 아니라 파생이다
 * @param cityChanged  직전 날짜와 기준 도시가 다르다 — 날짜 탭 왼쪽에 구분선이 붙는다
 */
public record TripDayResponse(
        Long dayId,
        int dayIndex,
        LocalDate date,
        String weekday,
        int legIndex,
        boolean cityChanged,
        String cityMemo,
        BaseCityResponse baseCity
) {
}
