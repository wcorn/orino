package ds.project.orino.planner.dashboard.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.dashboard.dto.DashboardHeatmapResponse;
import ds.project.orino.planner.dashboard.dto.DashboardStatisticsResponse;
import ds.project.orino.planner.dashboard.dto.DashboardStreaksResponse;
import ds.project.orino.planner.dashboard.dto.DashboardSummaryResponse;
import ds.project.orino.planner.dashboard.dto.StatisticsPeriod;
import ds.project.orino.planner.dashboard.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getSummary(
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getSummary(memberId)));
    }

    @GetMapping("/streaks")
    public ResponseEntity<ApiResponse<DashboardStreaksResponse>> getStreaks(
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getStreaks(memberId)));
    }

    @GetMapping("/heatmap")
    public ResponseEntity<ApiResponse<DashboardHeatmapResponse>> getHeatmap(
            Authentication authentication,
            @RequestParam(required = false) Integer year) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getHeatmap(memberId, year)));
    }

    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<DashboardStatisticsResponse>> getStatistics(
            Authentication authentication,
            @RequestParam(required = false) StatisticsPeriod period,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getStatistics(
                        memberId, period, startDate, endDate)));
    }
}
