package ds.project.orino.planner.lifelog.flow.dto;

import ds.project.orino.domain.planner.lifelog.entity.FlowStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 흐름 수정 요청. 기간(started/ended)은 담긴 기록에서 유도하므로 여기서 받지 않는다.
 *
 * @param title           제목(필수)
 * @param description     설명
 * @param coverObjectKey  커버 이미지 key(비우면 담긴 첫 사진으로 대체 표시)
 * @param status          ACTIVE/ARCHIVED
 */
public record FlowUpdateRequest(
        @NotBlank
        String title,
        String description,
        String coverObjectKey,
        @NotNull
        FlowStatus status
) {
}
