package ds.project.orino.planner.ledger.stats;

import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.planner.ledger.transaction.dto.TransactionView;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/** 복합 검색(§10.2). */
public final class LedgerSearchDtos {

    private LedgerSearchDtos() {
    }

    /**
     * 검색 조건. <b>기간만 필수다</b> — 나머지는 비우면 걸지 않는다.
     *
     * <p>기간을 필수로 두는 이유는 원장 전체를 훑는 질의를 실수로 부르지 않게 하려는 것이고,
     * 화면도 언제나 기간부터 고른다.
     */
    public record SearchRequest(
            @NotNull LocalDate from,
            @NotNull LocalDate to,
            LedgerFlow type,
            Long assetId,
            Long categoryId,
            Long minAmount,
            Long maxAmount,
            String keyword
    ) {
    }

    /**
     * @param total     조건에 걸린 <b>지출</b> 합계. 「작년에 스타벅스에 얼마 썼나」의 답이다
     * @param truncated 상한에 걸려 잘렸는가. <b>숨기지 않는다</b> — 잘린 줄 모르고 일괄 편집을
     *                  하면 「전부 고쳤다」고 믿은 채 일부만 바뀐다
     */
    public record SearchResponse(
            List<TransactionView> items,
            int count,
            long total,
            boolean truncated
    ) {
    }
}
