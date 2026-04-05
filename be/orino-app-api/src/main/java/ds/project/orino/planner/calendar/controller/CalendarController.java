package ds.project.orino.planner.calendar.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.calendar.dto.CompleteBlockResponse;
import ds.project.orino.planner.calendar.dto.DailyScheduleResponse;
import ds.project.orino.planner.calendar.dto.MonthlyScheduleResponse;
import ds.project.orino.planner.calendar.dto.PostponeBlockRequest;
import ds.project.orino.planner.calendar.dto.PostponeBlockResponse;
import ds.project.orino.planner.calendar.dto.ReorderBlockRequest;
import ds.project.orino.planner.calendar.dto.ReorderBlockResponse;
import ds.project.orino.planner.calendar.dto.WeeklyScheduleResponse;
import ds.project.orino.planner.calendar.service.CalendarService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<DailyScheduleResponse>> getDaily(
            Authentication authentication,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long memberId = (Long) authentication.getPrincipal();
        LocalDate target = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.success(
                calendarService.getDaily(memberId, target)));
    }

    @GetMapping("/weekly")
    public ResponseEntity<ApiResponse<WeeklyScheduleResponse>> getWeekly(
            Authentication authentication,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long memberId = (Long) authentication.getPrincipal();
        LocalDate target = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.success(
                calendarService.getWeekly(memberId, target)));
    }

    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<MonthlyScheduleResponse>> getMonthly(
            Authentication authentication,
            @RequestParam int year,
            @RequestParam int month) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                calendarService.getMonthly(memberId, year, month)));
    }

    @PatchMapping("/blocks/{blockId}/complete")
    public ResponseEntity<ApiResponse<CompleteBlockResponse>> complete(
            Authentication authentication,
            @PathVariable Long blockId) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                calendarService.completeBlock(memberId, blockId)));
    }

    @PutMapping("/blocks/{blockId}/reorder")
    public ResponseEntity<ApiResponse<ReorderBlockResponse>> reorder(
            Authentication authentication,
            @PathVariable Long blockId,
            @Valid @RequestBody ReorderBlockRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                calendarService.reorderBlock(memberId, blockId, request)));
    }

    @PostMapping("/blocks/{blockId}/postpone")
    public ResponseEntity<ApiResponse<PostponeBlockResponse>> postpone(
            Authentication authentication,
            @PathVariable Long blockId,
            @Valid @RequestBody PostponeBlockRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                calendarService.postponeBlock(memberId, blockId, request)));
    }
}
