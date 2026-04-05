package ds.project.orino.planner.rescheduling.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.rescheduling.dto.ApplyRescheduleRequest;
import ds.project.orino.planner.rescheduling.dto.ApplyRescheduleResponse;
import ds.project.orino.planner.rescheduling.dto.RescheduleOptionsResponse;
import ds.project.orino.planner.rescheduling.service.ReschedulingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rescheduling")
public class ReschedulingController {

    private final ReschedulingService reschedulingService;

    public ReschedulingController(ReschedulingService reschedulingService) {
        this.reschedulingService = reschedulingService;
    }

    @GetMapping("/options")
    public ResponseEntity<ApiResponse<RescheduleOptionsResponse>> getOptions(
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                reschedulingService.getOptions(memberId)));
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<ApplyRescheduleResponse>> apply(
            Authentication authentication,
            @Valid @RequestBody ApplyRescheduleRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                reschedulingService.apply(memberId, request.strategy())));
    }
}
