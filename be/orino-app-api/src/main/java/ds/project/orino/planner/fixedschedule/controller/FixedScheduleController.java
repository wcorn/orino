package ds.project.orino.planner.fixedschedule.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.fixedschedule.dto.CreateFixedScheduleRequest;
import ds.project.orino.planner.fixedschedule.dto.FixedScheduleResponse;
import ds.project.orino.planner.fixedschedule.dto.UpdateFixedScheduleRequest;
import ds.project.orino.planner.fixedschedule.service.FixedScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/fixed-schedules")
public class FixedScheduleController {

    private final FixedScheduleService fixedScheduleService;

    public FixedScheduleController(FixedScheduleService fixedScheduleService) {
        this.fixedScheduleService = fixedScheduleService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FixedScheduleResponse>>> getFixedSchedules(
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(
                ApiResponse.success(fixedScheduleService.getFixedSchedules(memberId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FixedScheduleResponse>> create(
            Authentication authentication,
            @Valid @RequestBody CreateFixedScheduleRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(fixedScheduleService.create(memberId, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FixedScheduleResponse>> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateFixedScheduleRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(
                ApiResponse.success(fixedScheduleService.update(memberId, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            Authentication authentication, @PathVariable Long id) {
        Long memberId = (Long) authentication.getPrincipal();
        fixedScheduleService.delete(memberId, id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
