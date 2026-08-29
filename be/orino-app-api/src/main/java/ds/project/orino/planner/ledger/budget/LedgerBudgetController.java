package ds.project.orino.planner.ledger.budget;

import ds.project.orino.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 예산(API 스펙 §7). {@code period}를 안 주면 오늘이 속한 구간이다. */
@RestController
@RequestMapping("/api/ledger/budget")
public class LedgerBudgetController {

    private final LedgerBudgetService budgetService;

    public LedgerBudgetController(LedgerBudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public ApiResponse<LedgerBudgetDtos.BudgetResponse> get(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) String period) {
        return ApiResponse.success(budgetService.get(memberId, period));
    }

    /** 통째로 갈아 끼운다 — 보낸 카테고리 목록이 곧 그 달의 전부다. */
    @PutMapping
    public ApiResponse<LedgerBudgetDtos.BudgetResponse> put(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) String period,
            @Valid @RequestBody LedgerBudgetDtos.PutRequest request) {
        return ApiResponse.success(budgetService.put(memberId, period, request));
    }
}
