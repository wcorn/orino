package ds.project.orino.planner.ledger.template;

import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 빠른 입력 템플릿 DTO 묶음. */
public final class LedgerTemplateDtos {

    private LedgerTemplateDtos() {
    }

    /** 날짜가 없다 — 템플릿으로 적는 건은 언제나 오늘이다. */
    public record Create(
            @NotBlank @Size(max = 60) String name,
            @NotNull LedgerFlow txType,
            @Positive long amount,
            @NotNull Long assetId,
            Long categoryId,
            @Size(max = 120) String title
    ) {
    }

    public record Update(
            @Size(max = 60) String name,
            LedgerFlow txType,
            Long amount,
            Long assetId,
            Long categoryId,
            @Size(max = 120) String title
    ) {
    }

    /**
     * @param useCount 많이 쓴 순으로 대시보드 칩에 노출된다. 순서를 사람이 관리하지 않는다
     */
    public record View(
            Long id,
            String name,
            LedgerFlow txType,
            long amount,
            Long assetId,
            String assetName,
            Long categoryId,
            String categoryName,
            String title,
            int useCount
    ) {

        public static View of(LedgerTransactionTemplate template,
                              String assetName, String categoryName) {
            return new View(template.getId(), template.getName(), template.getTxType(),
                    template.getAmount(), template.getAssetId(), assetName,
                    template.getCategoryId(), categoryName, template.getTitle(),
                    template.getUseCount());
        }
    }
}
