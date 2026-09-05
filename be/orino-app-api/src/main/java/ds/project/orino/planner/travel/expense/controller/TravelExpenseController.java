package ds.project.orino.planner.travel.expense.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.travel.expense.dto.ExpenseAttachRequest;
import ds.project.orino.planner.travel.expense.dto.ExpenseAttachResponse;
import ds.project.orino.planner.travel.expense.service.TravelExpenseService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행 경비의 <b>쓰기</b>(명세 v2.2 §18).
 *
 * <p>지출을 만드는 곳은 여기가 아니다 — 그건 가계부 API다({@code POST /api/ledger/transactions}).
 * 여행 전용 지출 엔드포인트를 두지 않는다는 규칙은 그대로고, 여기 있는 것은 <b>「어느 여행의
 * 지출인가」를 정하는 일</b>뿐이다. 그건 가계부가 아니라 여행이 아는 것이다.
 *
 * <p>가계부의 일괄 편집({@code /ledger/transactions/bulk})에 두지 않은 이유이기도 하다.
 * 거기 두면 가계부가 여행의 존재와 소유권을 알아야 하고, 의존이 양방향이 된다.
 */
@RestController
@RequestMapping("/api/travel")
public class TravelExpenseController {

    private final TravelExpenseService expenseService;

    public TravelExpenseController(TravelExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    /** 고른 거래를 이 여행에 붙인다. 다른 여행에 붙어 있던 것도 이 여행으로 옮겨 온다. */
    @PostMapping("/trips/{tripId}/expenses/attach")
    public ApiResponse<ExpenseAttachResponse> attach(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long tripId,
            @Valid @RequestBody ExpenseAttachRequest request) {
        return ApiResponse.success(
                expenseService.attach(memberId, tripId, request.transactionIds()));
    }

    /** 이 여행에서 뗀다. 거래는 지우지 않고 연결만 끊는다. */
    @PostMapping("/trips/{tripId}/expenses/detach")
    public ApiResponse<ExpenseAttachResponse> detach(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long tripId,
            @Valid @RequestBody ExpenseAttachRequest request) {
        return ApiResponse.success(
                expenseService.detach(memberId, tripId, request.transactionIds()));
    }
}
