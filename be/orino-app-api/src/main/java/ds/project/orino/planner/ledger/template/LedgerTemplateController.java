package ds.project.orino.planner.ledger.template;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.ledger.transaction.dto.TransactionCreatedResponse;
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

/** 빠른 입력 템플릿 API. 대시보드의 「빠른 입력」 칩이 이 목록을 읽는다. */
@RestController
@RequestMapping("/api/ledger/templates")
public class LedgerTemplateController {

    private final LedgerTemplateService templateService;

    public LedgerTemplateController(LedgerTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ApiResponse<List<LedgerTemplateDtos.View>> list(
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(templateService.list(memberId));
    }

    @PostMapping
    public ApiResponse<LedgerTemplateDtos.View> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody LedgerTemplateDtos.Create request) {
        return ApiResponse.success(templateService.create(memberId, request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<LedgerTemplateDtos.View> update(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody LedgerTemplateDtos.Update request) {
        return ApiResponse.success(templateService.update(memberId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
                                    @PathVariable Long id) {
        templateService.delete(memberId, id);
        return ApiResponse.success();
    }

    /** 한 번 눌러 <b>오늘 날짜로</b> 기록한다. 쓸 때마다 순위가 오른다. */
    @PostMapping("/{id}/apply")
    public ApiResponse<TransactionCreatedResponse> apply(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        return ApiResponse.success(templateService.apply(memberId, id));
    }
}
