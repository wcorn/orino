package ds.project.orino.planner.routine.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.routine.dto.CreateRoutineRequest;
import ds.project.orino.planner.routine.dto.RoutineCheckRequest;
import ds.project.orino.planner.routine.dto.RoutineCheckResponse;
import ds.project.orino.planner.routine.dto.RoutineDetailResponse;
import ds.project.orino.planner.routine.dto.RoutineExceptionRequest;
import ds.project.orino.planner.routine.dto.RoutineExceptionResponse;
import ds.project.orino.planner.routine.dto.RoutineResponse;
import ds.project.orino.planner.routine.dto.RoutineStatusRequest;
import ds.project.orino.planner.routine.dto.UpdateRoutineRequest;
import ds.project.orino.planner.routine.service.RoutineService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/routines")
public class RoutineController {

    private final RoutineService routineService;

    public RoutineController(RoutineService routineService) {
        this.routineService = routineService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoutineResponse>>> getRoutines(
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(
                ApiResponse.success(routineService.getRoutines(memberId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoutineDetailResponse>> getRoutine(
            Authentication authentication, @PathVariable Long id) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(
                ApiResponse.success(routineService.getRoutine(memberId, id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoutineResponse>> create(
            Authentication authentication,
            @Valid @RequestBody CreateRoutineRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        routineService.create(memberId, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoutineResponse>> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoutineRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                routineService.update(memberId, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            Authentication authentication, @PathVariable Long id) {
        Long memberId = (Long) authentication.getPrincipal();
        routineService.delete(memberId, id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<RoutineResponse>> changeStatus(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody RoutineStatusRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                routineService.changeStatus(memberId, id, request.status())));
    }

    @PostMapping("/{id}/check")
    public ResponseEntity<ApiResponse<RoutineCheckResponse>> check(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody RoutineCheckRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                routineService.check(memberId, id, request)));
    }

    @DeleteMapping("/{id}/check")
    public ResponseEntity<ApiResponse<Void>> uncheck(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate checkDate) {
        Long memberId = (Long) authentication.getPrincipal();
        routineService.uncheckByDate(memberId, id, checkDate);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/{id}/exceptions")
    public ResponseEntity<ApiResponse<RoutineExceptionResponse>> addException(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody RoutineExceptionRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        routineService.addException(memberId, id, request)));
    }

    @DeleteMapping("/{id}/exceptions/{exceptionId}")
    public ResponseEntity<ApiResponse<Void>> removeException(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long exceptionId) {
        Long memberId = (Long) authentication.getPrincipal();
        routineService.removeException(memberId, id, exceptionId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
