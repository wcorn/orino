package ds.project.orino.planner.travel.board.dto;

import ds.project.orino.domain.planner.travel.entity.TripStatus;
import ds.project.orino.planner.travel.activity.dto.ActivityResponse;
import ds.project.orino.planner.travel.route.dto.TravelTimeResponse;
import ds.project.orino.planner.travel.tools.dto.WeatherResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 보드(S-04)의 <b>단일 조회</b> 응답. 날짜 탭·보관함 건수·선택된 날짜의 일정을 한 번에 담는다.
 *
 * <p>화면이 N+1 호출을 하지 않게 하려는 것이고, 더 중요하게는 <b>오프라인 캐시가 응답 하나로
 * 성립해야</b> 하기 때문이다(4단계). 응답을 쪼개면 비행기 모드에서 탭 하나만 살아난다.
 *
 * @param days          여행 기간에서 만든 날짜 탭 전체. 보관함 칩은 FE가 뒤에 붙인다
 * @param selectedDate  이번 응답의 {@code activities}가 속한 날짜. {@code null}이면 보관함을 보고 있다.
 *                      요청이 날짜를 생략했을 때 서버가 무엇을 골랐는지 FE가 알아야 탭을 강조할 수 있다
 * @param archiveCount  미배정 보관함 일정 수(보관함 칩의 "{n}개")
 * @param activities    선택된 날짜(또는 보관함)의 일정만. {@code sortOrder} 순
 * @param travelTimes   연속한 두 일정 사이 이동시간(§4.4). 장소 없는 일정은 건너뛴다
 */
public record BoardResponse(
        BoardTrip trip,
        List<BoardDay> days,
        LocalDate selectedDate,
        long archiveCount,
        List<ActivityResponse> activities,
        List<TravelTimeResponse> travelTimes
) {

    /**
     * @param status     여행 타임존의 오늘로 파생한 상태
     * @param recordMode 완료된 여행이면 true — 계획 편집 대신 기록 입력을 보여준다
     */
    public record BoardTrip(
            Long id,
            String title,
            String timezone,
            String currency,
            LocalDate startDate,
            LocalDate endDate,
            TripStatus status,
            boolean recordMode
    ) {
    }

    /**
     * 날짜 탭 하나.
     *
     * @param dayIndex      1부터 시작하는 일차
     * @param weekday       한국어 요일 한 글자("금")
     * @param activityCount 그 날짜의 일정 수
     * @param weather       날씨 요약. 예보 범위(16일) 밖이면 null이다
     */
    public record BoardDay(
            int dayIndex,
            LocalDate date,
            String weekday,
            long activityCount,
            WeatherResponse.DailyWeather weather
    ) {
    }
}
