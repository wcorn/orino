package ds.project.orino.planner.ledger.subscription;

import ds.project.orino.common.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 청약 인정 현황 API(§10.2). 모든 값은 <b>추정</b>이다 — 정본은 청약홈이다.
 */
@RestController
@RequestMapping("/api/ledger/assets/{id}/subscription")
public class LedgerSubscriptionController {

    private final LedgerSubscriptionService subscriptionService;

    public LedgerSubscriptionController(LedgerSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public ApiResponse<SubscriptionDtos.Response> get(@AuthenticationPrincipal Long memberId,
                                                      @PathVariable Long id) {
        return ApiResponse.success(subscriptionService.get(memberId, id));
    }

    @PutMapping("/baseline")
    public ApiResponse<SubscriptionDtos.BaselineChange> updateBaseline(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @RequestBody SubscriptionDtos.BaselineRequest request) {
        return ApiResponse.success(subscriptionService.updateBaseline(memberId, id, request));
    }
}
