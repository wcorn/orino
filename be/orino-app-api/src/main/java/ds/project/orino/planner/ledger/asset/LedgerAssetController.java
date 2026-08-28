package ds.project.orino.planner.ledger.asset;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.ledger.asset.dto.AssetDetailResponse;
import ds.project.orino.planner.ledger.asset.dto.AssetListResponse;
import ds.project.orino.planner.ledger.asset.dto.AssetRequests;
import ds.project.orino.planner.ledger.asset.dto.AssetTransactionsResponse;
import ds.project.orino.planner.ledger.asset.dto.AssetView;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 자산 API.
 *
 * <p><b>자산을 지우는 엔드포인트가 없다.</b> 숨김({@code PATCH hidden=true})만 있다 —
 * 자산을 지우면 그 자산에 붙은 과거 거래가 갈 곳을 잃고, 그건 원장이 틀어지는 길이다.
 */
@RestController
@RequestMapping("/api/ledger")
public class LedgerAssetController {

    private final LedgerAssetService assetService;

    public LedgerAssetController(LedgerAssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping("/assets")
    public ApiResponse<AssetListResponse> list(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(assetService.list(memberId));
    }

    @PostMapping("/assets")
    public ApiResponse<AssetView> create(@AuthenticationPrincipal Long memberId,
                                         @Valid @RequestBody AssetRequests.Create request) {
        return ApiResponse.success(assetService.create(memberId, request));
    }

    @PatchMapping("/assets/{id}")
    public ApiResponse<AssetView> update(@AuthenticationPrincipal Long memberId,
                                         @PathVariable Long id,
                                         @Valid @RequestBody AssetRequests.Update request) {
        return ApiResponse.success(assetService.update(memberId, id, request));
    }

    /** 잔액 · 추이 · 카테고리 분포. 잔액은 원장에서 파생한 값이지 저장된 값이 아니다. */
    @GetMapping("/assets/{id}")
    public ApiResponse<AssetDetailResponse> detail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @RequestParam(required = false) AssetDetailResponse.Range range) {
        return ApiResponse.success(assetService.detail(memberId, id, range));
    }

    /** 줄마다 그 시점의 잔액이 붙는다 — 통장 거래내역처럼 읽힌다. */
    @GetMapping("/assets/{id}/transactions")
    public ApiResponse<AssetTransactionsResponse> transactions(
            @AuthenticationPrincipal Long memberId, @PathVariable Long id) {
        return ApiResponse.success(assetService.transactions(memberId, id));
    }

    @GetMapping("/asset-groups")
    public ApiResponse<List<AssetListResponse.GroupView>> groups(
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(assetService.list(memberId).groups());
    }

    @PostMapping("/asset-groups")
    public ApiResponse<List<AssetListResponse.GroupView>> createGroup(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody AssetRequests.GroupCreate request) {
        return ApiResponse.success(assetService.createGroup(memberId, request));
    }

    @PatchMapping("/asset-groups/{id}")
    public ApiResponse<List<AssetListResponse.GroupView>> updateGroup(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody AssetRequests.GroupUpdate request) {
        return ApiResponse.success(assetService.updateGroup(memberId, id, request));
    }
}
