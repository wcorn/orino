package ds.project.orino.planner.travel.expense.dto;

import ds.project.orino.domain.planner.travel.entity.TripExpenseCategory;
import ds.project.orino.domain.planner.travel.entity.TripExpenseStatus;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 지출 수정. <b>보낸 것만 바꾼다</b> — 빠진 필드는 건드리지 않는다.
 *
 * <p>비우려면 {@code clear*}를 쓴다. {@code null}은 「안 보냈다」와 「비우겠다」를 구분하지
 * 못하는데, 분류를 미분류로 되돌리거나 결제수단을 지우는 것은 실제로 일어나는 조작이다.
 * 가계부 거래 수정이 같은 모양이었다.
 *
 * <p>「확정」 버튼이 보내는 것도 이 요청이다 — {@code status: CONFIRMED} 하나다. 지난 예정을
 * 올려 주는 배치가 없으므로(§4.3) 이 길이 유일하다.
 */
public record ExpenseUpdateRequest(
        LocalDate occurredOn,
        Long amount,
        @Size(max = 100) String title,
        Boolean clearTitle,
        TripExpenseCategory category,
        Boolean clearCategory,
        @Size(max = 40) String paymentMethod,
        Boolean clearPaymentMethod,
        TripExpenseStatus status,
        ExpenseFxInput fx,
        /** 외화 근거를 지우고 원화 지출로 되돌린다. {@code amount}를 함께 보내야 한다. */
        Boolean clearFx
) {
}
