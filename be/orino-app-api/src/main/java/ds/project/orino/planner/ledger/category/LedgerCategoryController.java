package ds.project.orino.planner.ledger.category;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.planner.ledger.category.dto.CategoryDtos;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 카테고리 API. 처음 부르는 순간 기본 프리셋 13종이 심긴다(D-14). */
@RestController
@RequestMapping("/api/ledger/categories")
public class LedgerCategoryController {

    private final LedgerCategoryService categoryService;

    public LedgerCategoryController(LedgerCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<CategoryDtos.View>> list(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) LedgerFlow flow) {
        return ApiResponse.success(categoryService.list(memberId, flow));
    }

    @PostMapping
    public ApiResponse<CategoryDtos.View> create(@AuthenticationPrincipal Long memberId,
                                                 @Valid @RequestBody CategoryDtos.Create request) {
        return ApiResponse.success(categoryService.create(memberId, request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<CategoryDtos.View> update(@AuthenticationPrincipal Long memberId,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody CategoryDtos.Update request) {
        return ApiResponse.success(categoryService.update(memberId, id, request));
    }

    /** 보관 처리다. 붙어 있던 거래는 그대로 남는다 — 옮기려면 통합을 쓴다. */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
                                    @PathVariable Long id) {
        categoryService.archive(memberId, id);
        return ApiResponse.success();
    }

    /** 통합 — 내역이 따라온다. */
    @PatchMapping("/{id}/merge")
    public ApiResponse<CategoryDtos.MergeResponse> merge(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody CategoryDtos.MergeRequest request) {
        return ApiResponse.success(categoryService.merge(memberId, id, request));
    }
}
