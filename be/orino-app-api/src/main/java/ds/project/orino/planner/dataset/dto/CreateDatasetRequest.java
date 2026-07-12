package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateDatasetRequest(
        @NotEmpty(message = "columns는 최소 1개여야 합니다.")
        List<DatasetColumn> columns
) {
}
