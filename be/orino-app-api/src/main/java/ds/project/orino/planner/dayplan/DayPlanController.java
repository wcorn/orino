package ds.project.orino.planner.dayplan;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.dayplan.dto.DayPlanRequest;
import ds.project.orino.planner.dayplan.dto.DayPlanResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 주간 계획표 — 멤버 단일 주간 템플릿 조회 + 전량 교체. */
@RestController
@RequestMapping("/api/planner/plan")
public class DayPlanController {

    private final DayPlanService dayPlanService;

    public DayPlanController(DayPlanService dayPlanService) {
        this.dayPlanService = dayPlanService;
    }

    @GetMapping
    public ApiResponse<DayPlanResponse> get(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(dayPlanService.getWeeklyPlan(memberId));
    }

    @PutMapping
    public ApiResponse<DayPlanResponse> replace(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody DayPlanRequest request) {
        return ApiResponse.success(dayPlanService.replace(memberId, request));
    }
}
