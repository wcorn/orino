package ds.project.orino.planner.dataset.dto;

import java.util.List;

public record RowsResponse(
        List<RowView> rows,
        int offset,
        int limit
) {
}
