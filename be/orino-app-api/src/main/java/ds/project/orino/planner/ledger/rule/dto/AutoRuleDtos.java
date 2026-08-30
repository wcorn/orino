package ds.project.orino.planner.ledger.rule.dto;

import ds.project.orino.domain.planner.ledger.entity.LedgerMatchType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 자동 분류 규칙 입출력(`LDG-062`). */
public final class AutoRuleDtos {

    private AutoRuleDtos() {
    }

    /**
     * @param categoryName 규칙이 가리키는 카테고리 이름. 화면이 id로 다시 찾지 않게 함께 내린다
     */
    public record View(
            Long id,
            String keyword,
            LedgerMatchType matchType,
            Long categoryId,
            String categoryName,
            int priority,
            boolean enabled
    ) {
    }

    /**
     * @param priority 비우면 맨 뒤. 먼저 만든 규칙이 먼저 걸리는 것이 예측하기 쉽다
     */
    public record CreateRequest(
            @NotBlank @Size(max = 120) String keyword,
            @NotNull LedgerMatchType matchType,
            @NotNull Long categoryId,
            Integer priority
    ) {
    }

    /** 안 보낸 항목은 그대로 둔다. */
    public record UpdateRequest(
            @Size(max = 120) String keyword,
            LedgerMatchType matchType,
            Long categoryId,
            Integer priority,
            Boolean enabled
    ) {
    }
}
