package ds.project.orino.planner.travel.activity.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 하루의 일정을 통째로 다른 날짜와 맞바꾼다.
 *
 * <p><b>일정만 옮긴다.</b> 기준 도시·숙소·도시 메모는 날짜에 남는다 — 숙소는 체크인·체크아웃
 * 날짜에 묶여 있고, 기준 도시가 따라가면 구간이 통째로 다시 나뉘어 숙소와 어긋난다.
 * 바꾸려는 것은 "그날 무엇을 하는가"이지 "어디에 있는가"가 아니다.
 *
 * <p>보관함({@code null})은 받지 않는다. 보관함은 날짜가 아니라 날짜를 못 정한 일정이
 * 모이는 곳이라, 맞바꿀 "하루"가 없다.
 *
 * @param date     지금 보고 있는 날짜
 * @param withDate 맞바꿀 날짜. 비어 있어도 된다 — 그러면 그 날짜로 통째 이동이 된다
 */
public record DaySwapRequest(
        @NotNull(message = "바꿀 날짜를 보내주세요.")
        LocalDate date,

        @NotNull(message = "맞바꿀 날짜를 보내주세요.")
        LocalDate withDate
) {
}
