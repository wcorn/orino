package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 열 순서 변경 요청. 바뀐 위치 하나가 아니라 <b>전체 순서</b>를 받는다.
 * 멱등이고, 현재 열 집합과 정확히 일치하는지 한 번에 검증할 수 있다.
 */
public record ReorderColumnsRequest(
        @NotEmpty(message = "keys는 필수입니다.")
        List<String> keys
) {
}
