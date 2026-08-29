package ds.project.orino.planner.ledger.budget;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.List;

/** 예산(확정 명세 §9). */
public final class LedgerBudgetDtos {

    private LedgerBudgetDtos() {
    }

    public record PutRequest(
            @NotNull @PositiveOrZero Long totalAmount,
            @Valid List<CategoryAmount> categories) {
    }

    public record CategoryAmount(@NotNull Long categoryId,
                                 @NotNull @PositiveOrZero Long amount) {
    }

    /**
     * @param fixedCostTotal 정기 항목 월 환산 합. <b>미리 차감해 「쓸 수 있는 돈」만 남긴다</b>
     * @param spendable      {@code totalAmount − fixedCostTotal}. 음수면 예산이 고정비도 못 덮는다
     * @param spent          이미 쓴 돈. 게이지의 <b>진한</b> 부분
     * @param scheduled      아직 안 썼지만 나갈 게 확정된 돈. 게이지의 <b>연한</b> 부분(§8.2)
     * @param dailyAllowance 남은 일수로 나눈 하루 사용 가능액
     */
    public record BudgetResponse(
            String period,
            LocalDate periodStart,
            LocalDate periodEnd,
            long totalAmount,
            long fixedCostTotal,
            long spendable,
            long spent,
            long scheduled,
            long remaining,
            int daysLeft,
            long dailyAllowance,
            List<CategoryProgress> categories) {
    }

    /** 카테고리 한 줄. 한도를 안 정한 카테고리도 <b>쓴 돈이 있으면</b> 나온다. */
    public record CategoryProgress(
            Long categoryId,
            String name,
            long amount,
            long spent,
            long scheduled) {
    }
}
