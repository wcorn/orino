package ds.project.orino.planner.ledger.point;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.ledger.point.dto.PointDtos;
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

/**
 * 포인트·마일리지 API(`LDG-006`).
 *
 * <p>자산 API와 <b>따로</b> 둔다. 같은 목록에 실으면 언젠가 합계에 섞이고, 그 순간
 * 「자산이 얼마인가」가 답할 수 없는 질문이 된다.
 */
@RestController
@RequestMapping("/api/ledger/points")
public class LedgerPointController {

    private final LedgerPointService pointService;

    public LedgerPointController(LedgerPointService pointService) {
        this.pointService = pointService;
    }

    @GetMapping
    public ApiResponse<List<PointDtos.View>> list(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(pointService.list(memberId));
    }

    @PostMapping
    public ApiResponse<PointDtos.View> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PointDtos.SaveRequest request) {
        return ApiResponse.success(pointService.create(memberId, request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<PointDtos.View> update(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody PointDtos.UpdateRequest request) {
        return ApiResponse.success(pointService.update(memberId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
                                    @PathVariable Long id) {
        pointService.delete(memberId, id);
        return ApiResponse.success(null);
    }
}
