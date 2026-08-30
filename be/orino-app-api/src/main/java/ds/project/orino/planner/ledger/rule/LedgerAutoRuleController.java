package ds.project.orino.planner.ledger.rule;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.ledger.rule.dto.AutoRuleDtos;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 자동 분류 규칙 API(`LDG-062`). 가져오기와 수동 입력이 같은 규칙을 쓴다. */
@RestController
@RequestMapping("/api/ledger/auto-rules")
public class LedgerAutoRuleController {

    private final LedgerAutoRuleService autoRuleService;

    public LedgerAutoRuleController(LedgerAutoRuleService autoRuleService) {
        this.autoRuleService = autoRuleService;
    }

    @GetMapping
    public ApiResponse<List<AutoRuleDtos.View>> list(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(autoRuleService.list(memberId));
    }

    @PostMapping
    public ApiResponse<AutoRuleDtos.View> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody AutoRuleDtos.CreateRequest request) {
        return ApiResponse.success(autoRuleService.create(memberId, request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AutoRuleDtos.View> update(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody AutoRuleDtos.UpdateRequest request) {
        return ApiResponse.success(autoRuleService.update(memberId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
                                    @PathVariable Long id) {
        autoRuleService.delete(memberId, id);
        return ApiResponse.success(null);
    }
}
