package ds.project.orino.planner.preference.dto;

import ds.project.orino.domain.preference.entity.PriorityItemType;
import jakarta.validation.constraints.NotNull;

public record PriorityRuleRequest(
        @NotNull PriorityItemType itemType,
        int sortOrder
) {
}
