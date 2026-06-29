package ds.project.orino.planner.dayplan.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 주간 블록 요청. 시간은 사용자 로컬 벽시계 "HH:mm"(종료는 "24:00"=자정 허용).
 * 의미 검증(요일 범위·시각 파싱·end&gt;start·label 비어있음)은 서비스에서 PLN-ERR-002로 처리한다.
 *
 * @param dayOfWeek 요일 0=일 … 6=토
 */
public record DayPlanBlockRequest(
        int dayOfWeek,
        @NotBlank String startTime,
        @NotBlank String endTime,
        String label,
        String color
) {
}
