package ds.project.orino.planner.google.calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 일정 생성/수정 요청. 시간 값은 사용자 시간대 로컬 기준이다.
 *
 * @param allDay 종일 여부. 종일이면 start/end는 날짜("2026-06-10"), 아니면 datetime("2026-06-10T14:00:00")
 * @param end    종일이면 포함 마지막 날짜(서버가 Google 배타적 종료로 보정)
 */
public record EventRequest(
        @NotBlank String title,
        boolean allDay,
        @NotNull String start,
        @NotNull String end,
        String location,
        String description
) {
}
