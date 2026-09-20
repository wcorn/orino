package ds.project.orino.planner.travel.expense.dto;

import ds.project.orino.domain.planner.travel.entity.TripExpenseCategory;
import ds.project.orino.domain.planner.travel.entity.TripExpenseStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 지출 입력. 필수는 <b>날짜와 금액</b>뿐이다.
 *
 * <p>제목도 분류도 없이 저장할 수 있다(경비 독립 §6.1) — 그 길이 이 기능의 핵심이다.
 * 여행 중에 금액만 찍고 넘어갈 수 없으면 결국 안 적게 되고, 안 적힌 장부는 답을 못 한다.
 *
 * @param amount {@code fx}를 보냈으면 생략한다 — 그때는 서버가 {@code round(fx.amount × rate)}로
 *               확정한다. <b>둘 다 없으면 400</b>이다(TRAVEL-ERR-027)
 * @param status 안 보내면 날짜로 정한다 — 오늘 이후면 예정이다. 「예정으로 적기」를 따로
 *               외우게 하지 않는다. 보냈으면 그 값이 이긴다
 */
public record ExpenseCreateRequest(
        @NotNull LocalDate occurredOn,
        // @Positive를 걸지 않는다. 0 이하일 때 「잘못된 요청」이 아니라 「금액을 적어야
        // 합니다」(TRAVEL-ERR-027)로 답해야 한다 — 외화만 보낸 길과 같은 자리에서 같은
        // 문구가 나와야 화면이 한 곳만 보면 된다.
        Long amount,
        @Size(max = 100) String title,
        TripExpenseCategory category,
        @Size(max = 40) String paymentMethod,
        TripExpenseStatus status,
        ExpenseFxInput fx
) {
}
