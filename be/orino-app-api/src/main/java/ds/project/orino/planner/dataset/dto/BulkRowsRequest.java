package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 행 벌크 추가(끝에 append). Import 청크 업로드에 사용. */
public record BulkRowsRequest(
        @NotNull(message = "rows는 필수입니다.")
        List<List<String>> rows
) {
}
