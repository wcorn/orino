package ds.project.orino.planner.preference.dto;

import ds.project.orino.domain.preference.entity.PriorityItemType;
import ds.project.orino.domain.preference.entity.PriorityRule;

public record PriorityRuleResponse(
        Long id,
        PriorityItemType itemType,
        int sortOrder
) {

    public static PriorityRuleResponse from(PriorityRule r) {
        return new PriorityRuleResponse(
                r.getId(),
                r.getItemType(),
                r.getSortOrder()
        );
    }
}
