package ds.project.orino.planner.dayplan.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * 타임박스 블록 요청. 시간은 사용자 로컬 벽시계 "HH:mm".
 *
 * @param id        기존 블록 id(있으면 수정, 없으면 신규). PATCH의 declarative 교체에서 사용
 * @param startTime 시작(필수)
 * @param endTime   종료(null이면 시점 블록)
 * @param chime     알람 여부(true면 미러 ON 시 보조 캘린더에 푸시 — DP3)
 */
public record DayPlanBlockRequest(
        Long id,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
        LocalTime startTime,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
        LocalTime endTime,
        @NotBlank String label,
        boolean chime
) {
}
