package ds.project.orino.planner.goal.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.goal.dto.MonthlyGoalRequest;
import ds.project.orino.planner.goal.dto.MonthlyGoalResponse;
import ds.project.orino.planner.goal.service.MonthlyGoalService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planner/monthly-goals")
public class MonthlyGoalController {

    private final MonthlyGoalService monthlyGoalService;

    public MonthlyGoalController(MonthlyGoalService monthlyGoalService) {
        this.monthlyGoalService = monthlyGoalService;
    }

    @GetMapping("/{year}/{month}")
    public ApiResponse<MonthlyGoalResponse> get(
            @AuthenticationPrincipal Long memberId,
            @PathVariable int year,
            @PathVariable int month) {
        return ApiResponse.success(monthlyGoalService.find(memberId, year, month));
    }

    @PutMapping("/{year}/{month}")
    public ApiResponse<MonthlyGoalResponse> upsert(
            @AuthenticationPrincipal Long memberId,
            @PathVariable int year,
            @PathVariable int month,
            @RequestBody MonthlyGoalRequest request) {
        return ApiResponse.success(monthlyGoalService.upsert(memberId, year, month, request));
    }

    @DeleteMapping("/{year}/{month}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable int year,
            @PathVariable int month) {
        monthlyGoalService.delete(memberId, year, month);
        return ApiResponse.success(null);
    }
}
