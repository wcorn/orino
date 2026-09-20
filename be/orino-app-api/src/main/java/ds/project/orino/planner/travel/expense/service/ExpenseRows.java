package ds.project.orino.planner.travel.expense.service;

import ds.project.orino.domain.planner.travel.entity.TripExpense;
import ds.project.orino.planner.travel.expense.dto.TripExpenseResponse;

import java.time.LocalDate;

/**
 * 지출 한 줄을 응답 모양으로. <b>조회와 저장이 같은 줄을 내린다</b> — 저장하고 나면 화면이
 * 그 줄만 갈아 끼우면 되고, 목록을 다시 받아 올 이유가 없다.
 *
 * <p>여기 한 곳에 두는 이유가 그것이다. 두 군데서 만들면 필드가 하나 늘 때 한쪽만 고치게
 * 되고, 저장 직후의 줄과 새로고침 뒤의 줄이 달라진다.
 */
final class ExpenseRows {

    private ExpenseRows() {
    }

    /**
     * @param today 그 여행의 오늘. 「지난 예정」 판정에 쓴다 — 서버 로컬 날짜를 넘기지 않는다
     */
    static TripExpenseResponse.ExpenseRow of(TripExpense expense, LocalDate today) {
        return new TripExpenseResponse.ExpenseRow(
                expense.getId(),
                expense.getTitle(),
                expense.getAmount(),
                expense.hasFx() ? new TripExpenseResponse.FxView(
                        expense.getFxCurrency(), expense.getFxAmount(), expense.getFxRate())
                        : null,
                expense.getStatus().name(),
                expense.getCategory(),
                expense.getPaymentMethod(),
                expense.getCategory() == null,
                expense.isOverdue(today),
                expense.getOccurredOn());
    }
}
