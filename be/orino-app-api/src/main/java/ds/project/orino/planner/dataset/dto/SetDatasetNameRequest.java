package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.Size;

/**
 * 표 이름 설정/해제 요청. 빈 값(null·공백)이면 무명으로 되돌린다. 유일성은 강제하지 않는다 —
 * 표간 참조(#915)의 이름 해석은 현재 노트 안 표들 사이에서 FE가 다룬다.
 */
public record SetDatasetNameRequest(
        @Size(max = 255, message = "표 이름은 255자 이하여야 합니다.")
        String name
) {
}
