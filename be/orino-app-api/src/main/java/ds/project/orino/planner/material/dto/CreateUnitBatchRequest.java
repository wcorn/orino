package ds.project.orino.planner.material.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateUnitBatchRequest(
        @NotEmpty @Valid List<CreateUnitRequest> units
) {
}
