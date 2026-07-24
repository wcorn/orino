package ds.project.orino.planner.lifelog.flow.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.domain.planner.lifelog.entity.FlowStatus;
import ds.project.orino.planner.lifelog.flow.dto.AddMomentsRequest;
import ds.project.orino.planner.lifelog.flow.dto.FlowCreateRequest;
import ds.project.orino.planner.lifelog.flow.dto.FlowDetail;
import ds.project.orino.planner.lifelog.flow.dto.FlowSummary;
import ds.project.orino.planner.lifelog.flow.dto.FlowUpdateRequest;
import ds.project.orino.planner.lifelog.flow.dto.ReorderMomentsRequest;
import ds.project.orino.planner.lifelog.flow.service.FlowService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lifelog/flows")
public class FlowController {

    private final FlowService flowService;

    public FlowController(FlowService flowService) {
        this.flowService = flowService;
    }

    @PostMapping
    public ApiResponse<FlowSummary> create(@AuthenticationPrincipal Long memberId,
                                           @Valid @RequestBody FlowCreateRequest request) {
        return ApiResponse.success(flowService.create(memberId, request));
    }

    @GetMapping
    public ApiResponse<List<FlowSummary>> list(@AuthenticationPrincipal Long memberId,
                                               @RequestParam(required = false) FlowStatus status) {
        return ApiResponse.success(flowService.list(memberId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<FlowDetail> detail(@AuthenticationPrincipal Long memberId,
                                          @PathVariable Long id) {
        return ApiResponse.success(flowService.detail(memberId, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<FlowSummary> update(@AuthenticationPrincipal Long memberId,
                                           @PathVariable Long id,
                                           @Valid @RequestBody FlowUpdateRequest request) {
        return ApiResponse.success(flowService.update(memberId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
                                    @PathVariable Long id) {
        flowService.delete(memberId, id);
        return ApiResponse.success();
    }

    /** 기록 담기(단건/다건, 멱등). */
    @PostMapping("/{id}/moments")
    public ApiResponse<FlowDetail> addMoments(@AuthenticationPrincipal Long memberId,
                                              @PathVariable Long id,
                                              @RequestBody AddMomentsRequest request) {
        return ApiResponse.success(flowService.addMoments(memberId, id, request.allMomentIds()));
    }

    /** 기록 빼기 — 소속만 제거, 기록은 보존. */
    @DeleteMapping("/{id}/moments/{momentId}")
    public ApiResponse<Void> removeMoment(@AuthenticationPrincipal Long memberId,
                                          @PathVariable Long id,
                                          @PathVariable Long momentId) {
        flowService.removeMoment(memberId, id, momentId);
        return ApiResponse.success();
    }

    /** 흐름 내 순서 조정. */
    @PutMapping("/{id}/moments/order")
    public ApiResponse<FlowDetail> reorder(@AuthenticationPrincipal Long memberId,
                                           @PathVariable Long id,
                                           @Valid @RequestBody ReorderMomentsRequest request) {
        return ApiResponse.success(flowService.reorder(memberId, id, request.momentIds()));
    }
}
