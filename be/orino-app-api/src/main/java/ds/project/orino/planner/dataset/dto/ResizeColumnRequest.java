package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 열 너비 변경 요청. px 정수만 받는다.
 *
 * <p>기본 폭으로 되돌리는 것은 <b>너비 삭제</b>(DELETE)로 표현한다. 여기서 null을 허용하면
 * "값을 안 보냈다"와 "기본으로 되돌려라"를 구분할 수 없다.
 */
public record ResizeColumnRequest(
        @NotNull(message = "width는 필수입니다.")
        @Min(value = DatasetColumn.MIN_WIDTH, message = "width는 " + DatasetColumn.MIN_WIDTH + " 이상이어야 합니다.")
        @Max(value = DatasetColumn.MAX_WIDTH, message = "width는 " + DatasetColumn.MAX_WIDTH + " 이하여야 합니다.")
        Integer width
) {
}
