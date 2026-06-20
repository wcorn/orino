package ds.project.orino.planner.google.routine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 루틴 시리즈/인스턴스 편집 요청. 편집 대상 범위(scope)와 무관하게 새 내용을 전체 재기술한다.
 *
 * <p>{@code instance} 범위에서는 {@code recurrence}가 무시된다(단일 occurrence 예외이므로).
 *
 * @param allDay     종일 여부
 * @param start      종일이면 날짜, 아니면 datetime. following은 새 시리즈 시작(보통 instanceDate)
 * @param end        종일이면 포함 마지막 날짜, 아니면 종료 datetime
 * @param recurrence 반복 규칙(all/following에서 사용)
 * @param memo       메모(Google description, null 가능)
 */
public record RoutineEditRequest(
        @NotBlank String title,
        boolean allDay,
        @NotNull String start,
        @NotNull String end,
        @NotNull @Valid RoutineRecurrence recurrence,
        String memo
) {
}
