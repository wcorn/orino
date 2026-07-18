package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 열 기본 정렬 변경 요청. left/center/right만 받는다.
 *
 * <p>기본 정렬(left)로 되돌리는 것은 <b>정렬 삭제</b>(DELETE)로 표현한다. 여기서 null을 허용하면
 * "값을 안 보냈다"와 "기본으로 되돌려라"를 구분할 수 없다({@link ResizeColumnRequest 너비}와 같은 규칙).
 */
public record SetColumnAlignRequest(
        @NotNull(message = "align은 필수입니다.")
        @Pattern(regexp = "left|center|right", message = "허용되지 않은 정렬입니다.")
        String align
) {
}
