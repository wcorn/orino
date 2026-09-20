package ds.project.orino.planner.travel.expense.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.travel.expense.dto.BudgetRequest;
import ds.project.orino.planner.travel.expense.dto.BudgetResponse;
import ds.project.orino.planner.travel.expense.dto.ExpenseCreateRequest;
import ds.project.orino.planner.travel.expense.dto.ExpenseUpdateRequest;
import ds.project.orino.planner.travel.expense.dto.TripExpenseResponse;
import ds.project.orino.planner.travel.expense.service.TravelExpenseService;
import ds.project.orino.planner.travel.expense.service.TripExpenseQueryService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행 경비(경비 독립 §6).
 *
 * <p><b>지출을 만드는 곳이 여기로 왔다.</b> 예전에는 가계부 API로 나갔고
 * ({@code POST /api/ledger/transactions}) 여행이 갖는 것은 「어느 여행의 지출인가」를 정하는
 * 일뿐이었다(D-27). 가계부가 없어지면서 여행이 자기 장부를 갖고, FE가 다른 도메인 API로
 * 직접 쓰러 가는 구조도 여기서 끝난다.
 *
 * <p>사라진 것은 {@code /expenses/attach} · {@code /detach}다 — 붙일 원장이 없다.
 */
@RestController
@RequestMapping("/api/travel/trips/{tripId}")
public class TravelExpenseController {

    private final TravelExpenseService expenseService;
    private final TripExpenseQueryService queryService;

    public TravelExpenseController(TravelExpenseService expenseService,
                                   TripExpenseQueryService queryService) {
        this.expenseService = expenseService;
        this.queryService = queryService;
    }

    /**
     * 경비 화면 한 벌. 그 여행의 지출을 출발 전 · N일차·도시 · 다녀온 뒤로 묶어 내린다.
     */
    @GetMapping("/expenses")
    public ApiResponse<TripExpenseResponse> expenses(@AuthenticationPrincipal Long memberId,
                                                     @PathVariable Long tripId) {
        return ApiResponse.success(queryService.get(memberId, tripId));
    }

    /**
     * 지출 하나를 적는다. 제목도 분류도 없이 금액만으로 저장된다(§6.1).
     *
     * <p>응답은 <b>목록의 한 줄과 같은 모양</b>이라 화면이 그 줄만 갈아 끼우면 된다.
     */
    @PostMapping("/expenses")
    public ApiResponse<TripExpenseResponse.ExpenseRow> create(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long tripId,
            @Valid @RequestBody ExpenseCreateRequest request) {
        return ApiResponse.success(expenseService.create(memberId, tripId, request));
    }

    /** 보낸 것만 바꾼다. 지난 예정의 「확정」 버튼도 이 경로다 — {@code status} 하나다. */
    @PatchMapping("/expenses/{expenseId}")
    public ApiResponse<TripExpenseResponse.ExpenseRow> update(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long tripId,
            @PathVariable Long expenseId,
            @Valid @RequestBody ExpenseUpdateRequest request) {
        return ApiResponse.success(
                expenseService.update(memberId, tripId, expenseId, request));
    }

    /** 소프트 삭제. <b>상쇄하지 않는다</b> — 여행 경비는 원장이 아니다(§4.4). */
    @DeleteMapping("/expenses/{expenseId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
                                    @PathVariable Long tripId,
                                    @PathVariable Long expenseId) {
        expenseService.delete(memberId, tripId, expenseId);
        return ApiResponse.success();
    }

    /** {@code amount}가 {@code null}이면 해제, 0이면 400이다. */
    @PutMapping("/budget")
    public ApiResponse<BudgetResponse> putBudget(@AuthenticationPrincipal Long memberId,
                                                 @PathVariable Long tripId,
                                                 @RequestBody BudgetRequest request) {
        return ApiResponse.success(new BudgetResponse(
                expenseService.updateBudget(memberId, tripId, request.amount())));
    }
}
