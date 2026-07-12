package ds.project.orino.planner.dataset.dto;

import ds.project.orino.domain.planner.dataset.entity.Dataset;

import java.util.List;

public record DatasetResponse(
        Long id,
        List<DatasetColumn> columns,
        int rowCount
) {
    public static DatasetResponse of(Dataset dataset, List<DatasetColumn> columns) {
        return new DatasetResponse(dataset.getId(), columns, dataset.getRowCount());
    }
}
