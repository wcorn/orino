package ds.project.orino.planner.dayplan.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * 주간 블록 요청. 시간은 사용자 로컬 벽시계 "HH:mm". 의미 검증(요일 범위·end&gt;start·label 비어있음)은
 * 서비스에서 PLN-ERR-002로 처리한다.
 *
 * @param dayOfWeek 요일 0=일 … 6=토
 */
public record DayPlanBlockRequest(
        int dayOfWeek,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime startTime,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime endTime,
        String label,
        String color
) {
}
