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
     * @param tripExpense    이 구간에 <b>여행으로</b> 쓴 돈. {@code spent}에도 게이지에도
     *                       들어가지 않는다 — 넣으면 여행 간 달은 항상 예산 초과가 되고,
     *                       그러면 그 게이지는 아무것도 알려주지 않는다(§9 · 여행 v2.2 §5.2).
     *                       <b>그래도 내려준다</b>: 빼놓고 말하지 않으면 합계가 안 맞는 것으로 보인다
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
            long tripExpense,
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
