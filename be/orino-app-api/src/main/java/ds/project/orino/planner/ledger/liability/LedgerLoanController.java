package ds.project.orino.planner.ledger.liability;

import ds.project.orino.common.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 대출 API(§10.3). 대출 자산 자체는 {@code POST /assets}가 만든다 — 여기는 그 뒤의 일이다.
 *
 * <p>상환 처리({@code /loans/{id}/settlements})는 아직 없다(#1401).
 */
@RestController
@RequestMapping("/api/ledger")
public class LedgerLoanController {

    private final LedgerLoanService loanService;

    public LedgerLoanController(LedgerLoanService loanService) {
        this.loanService = loanService;
    }

    /** 잔여 원금 · 기준값 · 금리 이력. 잔여 원금은 저장된 값이 아니라 원장에서 센 값이다. */
    @GetMapping("/loans/{assetId}")
    public ApiResponse<LoanDtos.Response> get(@AuthenticationPrincipal Long memberId,
                                              @PathVariable Long assetId) {
        return ApiResponse.success(loanService.get(memberId, assetId));
    }

    /** 기준 원금 다시 맞추기. 응답의 {@code before}로 화면이 차이를 한 번 알린다. */
    @PutMapping("/loans/{assetId}/baseline")
    public ApiResponse<LoanDtos.BaselineChange> updateBaseline(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long assetId,
            @RequestBody LoanDtos.BaselineRequest request) {
        return ApiResponse.success(loanService.updateBaseline(memberId, assetId, request));
    }

    /** 금리 변경. 대출·마이너스통장 공용이다. 최신이 위인 금리 이력을 돌려준다. */
    @PostMapping("/rates")
    public ApiResponse<List<LoanDtos.Rate>> changeRate(@AuthenticationPrincipal Long memberId,
                                                       @RequestBody LoanDtos.RateRequest request) {
        return ApiResponse.success(loanService.changeRate(memberId, request));
    }
}
