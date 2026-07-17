package ds.project.orino.planner.dataset.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateDatasetRequest(
        // @Valid — 생성 시에도 열의 width 범위를 검증한다. 없으면 resize API의 상·하한을
        // 생성 경로로 우회할 수 있다.
        @NotEmpty(message = "columns는 최소 1개여야 합니다.")
        @Valid
        List<DatasetColumn> columns
) {
}
