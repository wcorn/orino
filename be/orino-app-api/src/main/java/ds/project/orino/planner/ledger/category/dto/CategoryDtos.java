package ds.project.orino.planner.ledger.category.dto;

import ds.project.orino.domain.planner.ledger.entity.LedgerCategory;
import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 카테고리 DTO 묶음. */
public final class CategoryDtos {

    private CategoryDtos() {
    }

    /**
     * @param parentId 대분류 id. <b>2단까지만</b>이라 이 값이 가리키는 카테고리는
     *                 반드시 대분류여야 한다(LDG-ERR-015)
     */
    public record Create(
            @NotNull LedgerFlow flow,
            @NotBlank @Size(max = 40) String name,
            Long parentId,
            @Size(max = 20) String color,
            @Size(max = 40) String icon,
            Integer displayOrder
    ) {
    }

    public record Update(
            @Size(max = 40) String name,
            Long parentId,
            Boolean clearParent,
            @Size(max = 20) String color,
            @Size(max = 40) String icon,
            Integer displayOrder
    ) {
    }

    public record MergeRequest(@NotNull Long targetCategoryId) {
    }

    /**
     * 통합 결과.
     *
     * @param movedTransactions 함께 따라온 거래 수. <b>지워진 거래는 없다</b> —
     *                          소속만 옮기고 원본 카테고리는 보관 처리된다
     */
    public record MergeResponse(long movedTransactions) {
    }

    /** 카테고리 한 줄. 하위 분류는 {@code children}에 담긴다 — 2단이므로 그 안은 비어 있다. */
    public record View(
            Long id,
            LedgerFlow flow,
            String name,
            Long parentId,
            String color,
            String icon,
            int displayOrder,
            boolean archived,
            List<View> children
    ) {

        public static View of(LedgerCategory category, List<View> children) {
            return new View(category.getId(), category.getFlow(), category.getName(),
                    category.getParentId(), category.getColor(), category.getIcon(),
                    category.getDisplayOrder(), category.isArchived(), children);
        }
    }
}
