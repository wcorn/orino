package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 행 높이 변경 요청. px 정수만 받는다.
 *
 * <p>기본 높이로 되돌리는 것은 <b>높이 삭제</b>(DELETE)로 표현한다 — 열 너비와 같은 규칙.
 */
public record SetRowHeightRequest(
        @NotNull(message = "height는 필수입니다.")
        @Min(value = MIN_HEIGHT, message = "height는 " + MIN_HEIGHT + " 이상이어야 합니다.")
        @Max(value = MAX_HEIGHT, message = "height는 " + MAX_HEIGHT + " 이하여야 합니다.")
        Integer height
) {
    /** 행 높이 하한(px). 이보다 낮으면 값이 사실상 안 보인다. */
    public static final int MIN_HEIGHT = 24;
    /** 행 높이 상한(px). 한 행이 표를 다 밀어내는 것을 막는다. */
    public static final int MAX_HEIGHT = 600;
}
