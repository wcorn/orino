package ds.project.orino.planner.material.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.material.dto.AllocationRequest;
import ds.project.orino.planner.material.dto.AllocationResponse;
import ds.project.orino.planner.material.dto.CreateMaterialRequest;
import ds.project.orino.planner.material.dto.CreateUnitBatchRequest;
import ds.project.orino.planner.material.dto.CreateUnitRequest;
import ds.project.orino.planner.material.dto.DailyOverrideRequest;
import ds.project.orino.planner.material.dto.DailyOverrideResponse;
import ds.project.orino.planner.material.dto.MaterialDetailResponse;
import ds.project.orino.planner.material.dto.MaterialResponse;
import ds.project.orino.planner.material.dto.ReviewConfigRequest;
import ds.project.orino.planner.material.dto.ReviewConfigResponse;
import ds.project.orino.planner.material.dto.UnitResponse;
import ds.project.orino.planner.material.dto.UpdateMaterialRequest;
import ds.project.orino.planner.material.dto.UpdateUnitRequest;
import ds.project.orino.planner.material.service.MaterialService;
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
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    // --- Material CRUD ---

    @GetMapping
    public ResponseEntity<ApiResponse<List<MaterialResponse>>>
    getMaterials(Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                materialService.getMaterials(memberId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MaterialResponse>> create(
            Authentication authentication,
            @Valid @RequestBody CreateMaterialRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        materialService.create(memberId, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MaterialDetailResponse>>
    getMaterial(Authentication authentication,
               @PathVariable Long id) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                materialService.getMaterial(memberId, id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MaterialResponse>> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateMaterialRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                materialService.update(memberId, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            Authentication authentication,
            @PathVariable Long id) {
        Long memberId = (Long) authentication.getPrincipal();
        materialService.delete(memberId, id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PatchMapping("/{id}/pause")
    public ResponseEntity<ApiResponse<MaterialResponse>> pause(
            Authentication authentication,
            @PathVariable Long id) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                materialService.pause(memberId, id)));
    }

    @PatchMapping("/{id}/resume")
    public ResponseEntity<ApiResponse<MaterialResponse>> resume(
            Authentication authentication,
            @PathVariable Long id) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                materialService.resume(memberId, id)));
    }

    // --- Study Unit ---

    @PostMapping("/{id}/units")
    public ResponseEntity<ApiResponse<UnitResponse>> createUnit(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody CreateUnitRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        materialService.createUnit(
                                memberId, id, request)));
    }

    @PostMapping("/{id}/units/batch")
    public ResponseEntity<ApiResponse<List<UnitResponse>>>
    createUnitsBatch(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody CreateUnitBatchRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        materialService.createUnitsBatch(
                                memberId, id, request)));
    }

    @PutMapping("/{materialId}/units/{id}")
    public ResponseEntity<ApiResponse<UnitResponse>> updateUnit(
            Authentication authentication,
            @PathVariable Long materialId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUnitRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                materialService.updateUnit(
                        memberId, materialId, id, request)));
    }

    @DeleteMapping("/{materialId}/units/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUnit(
            Authentication authentication,
            @PathVariable Long materialId,
            @PathVariable Long id) {
        Long memberId = (Long) authentication.getPrincipal();
        materialService.deleteUnit(memberId, materialId, id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // --- Allocation ---

    @PutMapping("/{id}/allocation")
    public ResponseEntity<ApiResponse<AllocationResponse>>
    updateAllocation(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody AllocationRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                materialService.updateAllocation(
                        memberId, id, request)));
    }

    // --- Daily Override ---

    @GetMapping("/{id}/daily-overrides")
    public ResponseEntity<ApiResponse<List<DailyOverrideResponse>>>
    getDailyOverrides(Authentication authentication,
                      @PathVariable Long id) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                materialService.getDailyOverrides(memberId, id)));
    }

    @PutMapping("/{id}/daily-overrides/{date}")
    public ResponseEntity<ApiResponse<DailyOverrideResponse>>
    updateDailyOverride(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody DailyOverrideRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                materialService.updateDailyOverride(
                        memberId, id, date, request)));
    }

    @DeleteMapping("/{id}/daily-overrides/{date}")
    public ResponseEntity<ApiResponse<Void>> deleteDailyOverride(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long memberId = (Long) authentication.getPrincipal();
        materialService.deleteDailyOverride(memberId, id, date);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // --- Review Config ---

    @PutMapping("/{id}/review-config")
    public ResponseEntity<ApiResponse<ReviewConfigResponse>>
    updateReviewConfig(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody ReviewConfigRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                materialService.updateReviewConfig(
                        memberId, id, request)));
    }
}
