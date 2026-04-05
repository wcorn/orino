package ds.project.orino.planner.reflection.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.reflection.dto.CreateReflectionRequest;
import ds.project.orino.planner.reflection.dto.ReflectionResponse;
import ds.project.orino.planner.reflection.dto.UpdateReflectionRequest;
import ds.project.orino.planner.reflection.service.ReflectionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reflections")
public class ReflectionController {

    private final ReflectionService reflectionService;

    public ReflectionController(ReflectionService reflectionService) {
        this.reflectionService = reflectionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ReflectionResponse>> get(
            Authentication authentication,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                reflectionService.getByDate(memberId, date)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReflectionResponse>> create(
            Authentication authentication,
            @Valid @RequestBody CreateReflectionRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                reflectionService.create(memberId, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ReflectionResponse>> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateReflectionRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                reflectionService.update(memberId, id, request)));
    }
}
