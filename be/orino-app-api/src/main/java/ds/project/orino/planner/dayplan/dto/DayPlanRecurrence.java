package ds.project.orino.planner.dayplan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * 플랜 반복 규칙(요청·응답 공용). RRULE 문자열이 아니라 구조화된 형태.
 *
 * @param freq       DAILY|WEEKLY|MONTHLY (필수)
 * @param interval   반복 간격 N(null=1)
 * @param byDay      WEEKLY 요일 코드 목록 ["MO".."SU"] (그 외 freq면 무시)
 * @param byMonthDay MONTHLY 일자 목록 [1..31] (그 외 freq면 무시)
 * @param startsOn   첫 적용일(DTSTART, 필수)
 * @param until      종료일(포함). null=무한
 */
public record DayPlanRecurrence(
        @NotBlank String freq,
        Integer interval,
        List<String> byDay,
        List<Integer> byMonthDay,
        @NotNull LocalDate startsOn,
        LocalDate until
) {
}
