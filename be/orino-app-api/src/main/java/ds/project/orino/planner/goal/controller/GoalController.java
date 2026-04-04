package ds.project.orino.planner.goal.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.goal.dto.CreateGoalRequest;
import ds.project.orino.planner.goal.dto.CreateMilestoneRequest;
import ds.project.orino.planner.goal.dto.GoalDetailResponse;
import ds.project.orino.planner.goal.dto.GoalResponse;
import ds.project.orino.planner.goal.dto.GoalStatusRequest;
import ds.project.orino.planner.goal.dto.MilestoneResponse;
import ds.project.orino.planner.goal.dto.UpdateGoalRequest;
import ds.project.orino.planner.goal.dto.UpdateMilestoneRequest;
import ds.project.orino.planner.goal.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GoalResponse>>> getGoals(Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(goalService.getGoals(memberId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GoalDetailResponse>> getGoal(
            Authentication authentication, @PathVariable Long id) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(goalService.getGoal(memberId, id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GoalResponse>> create(
            Authentication authentication,
            @Valid @RequestBody CreateGoalRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(goalService.create(memberId, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GoalResponse>> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateGoalRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(goalService.update(memberId, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            Authentication authentication, @PathVariable Long id) {
        Long memberId = (Long) authentication.getPrincipal();
        goalService.delete(memberId, id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<GoalResponse>> changeStatus(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody GoalStatusRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(goalService.changeStatus(memberId, id, request.status())));
    }

    @PostMapping("/{id}/milestones")
    public ResponseEntity<ApiResponse<MilestoneResponse>> createMilestone(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody CreateMilestoneRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(goalService.createMilestone(memberId, id, request)));
    }

    @PutMapping("/{goalId}/milestones/{id}")
    public ResponseEntity<ApiResponse<MilestoneResponse>> updateMilestone(
            Authentication authentication,
            @PathVariable Long goalId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateMilestoneRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                goalService.updateMilestone(memberId, goalId, id, request)));
    }

    @DeleteMapping("/{goalId}/milestones/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMilestone(
            Authentication authentication,
            @PathVariable Long goalId,
            @PathVariable Long id) {
        Long memberId = (Long) authentication.getPrincipal();
        goalService.deleteMilestone(memberId, goalId, id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PatchMapping("/{goalId}/milestones/{id}/complete")
    public ResponseEntity<ApiResponse<MilestoneResponse>> completeMilestone(
            Authentication authentication,
            @PathVariable Long goalId,
            @PathVariable Long id) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                goalService.completeMilestone(memberId, goalId, id)));
    }
}
