package ds.project.orino.planner.dayplan;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.dayplan.dto.DayPlanListResponse;
import ds.project.orino.planner.dayplan.dto.DayPlanRequest;
import ds.project.orino.planner.dayplan.dto.DayPlanResponse;
import ds.project.orino.planner.dayplan.dto.PlanInstancesResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.time.LocalDate;

/** 데일리 플랜 CRUD + 배경 레이어 펼침. Google 무관. */
@RestController
@RequestMapping("/api/planner/plans")
public class DayPlanController {

    private final DayPlanService dayPlanService;

    public DayPlanController(DayPlanService dayPlanService) {
        this.dayPlanService = dayPlanService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DayPlanResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody DayPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(dayPlanService.create(memberId, request)));
    }

    @GetMapping
    public ApiResponse<DayPlanListResponse> list(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(dayPlanService.list(memberId));
    }

    @GetMapping("/instances")
    public ApiResponse<PlanInstancesResponse> instances(
            @AuthenticationPrincipal Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(dayPlanService.instances(memberId, from, to));
    }

    @PatchMapping("/{planId}")
    public ApiResponse<DayPlanResponse> update(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long planId,
            @Valid @RequestBody DayPlanRequest request) {
        return ApiResponse.success(dayPlanService.update(memberId, planId, request));
    }

    @DeleteMapping("/{planId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long planId) {
        dayPlanService.delete(memberId, planId);
        return ApiResponse.success();
    }
}
