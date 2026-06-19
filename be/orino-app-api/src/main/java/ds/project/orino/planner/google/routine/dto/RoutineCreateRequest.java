package ds.project.orino.planner.google.routine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 루틴 생성 요청. 시간 값은 사용자 시간대 로컬 기준이다.
 *
 * @param type       "habit"(종일 체크형) | "schedule"(시간 고정)
 * @param allDay     종일 여부. habit은 보통 true
 * @param start      종일이면 날짜("2026-06-20"), 아니면 datetime("2026-06-20T07:00:00")
 * @param end        종일이면 포함 마지막 날짜(보통 start와 동일), 아니면 종료 datetime
 * @param recurrence 반복 규칙(필수)
 * @param memo       메모(Google description으로 저장, null 가능)
 * @param color      표시 색상(현재 미사용, null 가능)
 */
public record RoutineCreateRequest(
        @NotBlank String type,
        @NotBlank String title,
        boolean allDay,
        @NotNull String start,
        @NotNull String end,
        @NotNull @Valid RoutineRecurrence recurrence,
        String memo,
        String color
) {
}
