package ds.project.orino.planner.preference.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdatePriorityRulesRequest(
        @NotEmpty @Valid List<PriorityRuleRequest> rules
) {
}
