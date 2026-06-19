package ds.project.orino.planner.google.routine.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

/**
 * 루틴 반복 규칙(요청·응답 공용). {@code RecurrenceRule} VO와 1:1 대응한다.
 *
 * @param freq       "DAILY" | "WEEKLY" | "MONTHLY"
 * @param interval   반복 간격(N). null이면 1
 * @param byDay      WEEKLY 요일 코드 목록(["MO","WE","FR"]). null/빈 값 허용
 * @param byMonthDay MONTHLY 일자 목록([1,15], 1~31). null/빈 값 허용
 * @param until      종료일(포함). null이면 무한 반복
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoutineRecurrence(
        @NotBlank String freq,
        Integer interval,
        List<String> byDay,
        List<Integer> byMonthDay,
        LocalDate until
) {
}
