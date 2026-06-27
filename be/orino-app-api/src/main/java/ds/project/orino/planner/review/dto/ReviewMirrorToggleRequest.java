package ds.project.orino.planner.review.dto;

import jakarta.validation.constraints.NotNull;

/** 복습 미러 on/off 토글 요청. */
public record ReviewMirrorToggleRequest(
        @NotNull(message = "enabled는 필수입니다.")
        Boolean enabled
) {
}
