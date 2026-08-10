package ds.project.orino.planner.travel.board.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import ds.project.orino.domain.planner.travel.entity.TripStatus;
import ds.project.orino.planner.travel.activity.dto.ActivityResponse;
import ds.project.orino.planner.travel.day.dto.BaseCityResponse;
import ds.project.orino.planner.travel.route.client.TravelMode;
import ds.project.orino.planner.travel.route.dto.TravelTimeResponse;
import ds.project.orino.planner.travel.tools.dto.WeatherResponse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 보드(S-04)의 단일 조회.
 *
 * <p>날짜 탭·보관함 건수·선택된 날짜의 일정을 한 응답에 담는다. 화면의 N+1 호출을 막는 것도
 * 있지만, 진짜 이유는 <b>오프라인 캐시가 응답 하나로 성립해야</b> 한다는 것이다 —
 * 응답을 쪼개면 비행기 모드에서 탭 하나만 살아난다.
 *
 * <p><b>v2.1 — 타임존·통화가 여행이 아니라 날짜에 붙는다.</b> 화면이 쓰던 {@code trip.timezone}
 * 자리는 {@code days[i].baseCity}로 옮겼다.
 *
 * @param days          여행 기간에서 만든 날짜 탭 전체. 보관함 칩은 FE가 뒤에 붙인다
 * @param selectedDate  이번 응답의 {@code activities}가 속한 날짜. {@code null}이면 보관함을 보고 있다.
 *                      요청이 날짜를 생략했을 때 서버가 무엇을 골랐는지 FE가 알아야 탭을 강조할 수 있다
 * @param archiveCount  미배정 보관함 일정 수(보관함 칩의 "{n}개")
 * @param activities    선택된 날짜(또는 보관함)의 일정만. {@code sortOrder} 순
 * @param travelTimes   연속한 두 일정 사이 이동시간(§4.4). 장소 없는 일정은 건너뛴다
 * @param stayMove      마지막 일정 → 숙소 이동. 숙소는 3단계라 지금은 항상 {@code null}
 */
public record BoardResponse(
        BoardTrip trip,
        List<BoardDay> days,
        LocalDate selectedDate,
        long archiveCount,
        List<ActivityResponse> activities,
        List<TravelTimeResponse> travelTimes,
        StayMove stayMove
) {

    /**
     * @param status      기준 도시 타임존의 오늘로 파생한 상태
     * @param recordMode  완료된 여행이면 true — 계획 편집 대신 기록 입력을 보여준다
     * @param cityCount   기간에 등장하는 <b>서로 다른 도시</b> 수(같은 도시를 다시 방문해도 1)
     * @param singleCity  전 기간이 한 도시다 — 날짜 탭을 {@code N일차}로 그린다
     */
    public record BoardTrip(
            Long id,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            TripStatus status,
            boolean recordMode,
            int cityCount,
            int countryCount,
            boolean singleCity
    ) {
    }

    /**
     * 날짜 탭 하나.
     *
     * @param dayIndex      1부터 시작하는 일차
     * @param weekday       한국어 요일 한 글자("금")
     * @param activityCount 그 날짜의 일정 수
     * @param baseCity      그날의 기준 도시 — 타임존·통화·날씨 좌표가 전부 여기서 나온다
     * @param cityChanged   직전 날짜와 도시가 다르다 → 탭 왼쪽에 구분선
     * @param legIndex      이 날짜가 속한 구간 번호(1부터). 저장값이 아니라 파생이다
     * @param weather       날씨 요약. 예보 범위(16일) 밖이면 null이다
     * @param stayTonight   오늘 밤 자는 곳. 숙소는 3단계라 지금은 항상 null
     * @param stayCheckout  오늘 체크아웃하는 곳. 3단계까지 null
     */
    public record BoardDay(
            Long dayId,
            int dayIndex,
            LocalDate date,
            String weekday,
            long activityCount,
            BaseCityResponse baseCity,
            boolean cityChanged,
            int legIndex,
            String cityMemo,
            WeatherResponse.DailyWeather weather,
            StayTonight stayTonight,
            StayCheckout stayCheckout
    ) {
    }

    /**
     * 오늘 밤 자는 곳. <b>3단계에 테이블이 채워지기 전까지 항상 null</b>이다 —
     * 형태를 지금 확정해 두면 FE가 두 번 고치지 않는다.
     *
     * @param sameCity     그날 기준 도시와 같은 도시인가. 닛코 당일치기 날 도쿄에서 자면 false
     * @param isCheckInDay 오늘 체크인하는 날인가 — 배지에 시각을 함께 보여줄지 가른다
     */
    public record StayTonight(
            Long stayId,
            String name,
            boolean sameCity,
            @JsonFormat(pattern = "HH:mm") LocalTime checkInTime,
            boolean isCheckInDay
    ) {
    }

    /** 오늘 체크아웃하는 곳. 3단계까지 null. */
    public record StayCheckout(
            Long stayId,
            String name,
            @JsonFormat(pattern = "HH:mm") LocalTime checkOutTime
    ) {
    }

    /**
     * 리스트 맨 아래 붙는 숙소 이동. 3단계까지 null.
     *
     * <p>{@code sameCity = false}면 {@code mode}·{@code durationMinutes}가 null이다 —
     * 도시를 넘는 이동은 계산하지 않는다(v2.1 §3.4).
     */
    public record StayMove(
            Long stayId,
            boolean sameCity,
            TravelMode mode,
            Integer durationMinutes
    ) {
    }
}
