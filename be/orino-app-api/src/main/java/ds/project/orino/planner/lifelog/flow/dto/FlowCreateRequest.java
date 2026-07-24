package ds.project.orino.planner.lifelog.flow.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 흐름 생성 요청.
 *
 * @param title       흐름 제목(필수)
 * @param description 설명
 */
public record FlowCreateRequest(
        @NotBlank
        String title,
        String description
) {
}
