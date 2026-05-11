package ds.project.orino.planner.material.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.domain.planner.material.entity.MaterialStatus;
import ds.project.orino.planner.material.dto.MaterialCreateRequest;
import ds.project.orino.planner.material.dto.MaterialDetailResponse;
import ds.project.orino.planner.material.dto.MaterialListResponse;
import ds.project.orino.planner.material.dto.MaterialSummaryResponse;
import ds.project.orino.planner.material.dto.MaterialUpdateRequest;
import ds.project.orino.planner.material.service.StudyMaterialService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planner/materials")
public class StudyMaterialController {

    private final StudyMaterialService studyMaterialService;

    public StudyMaterialController(StudyMaterialService studyMaterialService) {
        this.studyMaterialService = studyMaterialService;
    }

    @GetMapping
    public ApiResponse<MaterialListResponse> list(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) MaterialStatus status) {
        return ApiResponse.success(new MaterialListResponse(
                studyMaterialService.findAll(memberId, status)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MaterialSummaryResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody MaterialCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(studyMaterialService.create(memberId, request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<MaterialDetailResponse> detail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        return ApiResponse.success(studyMaterialService.findOne(memberId, id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<MaterialSummaryResponse> update(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody MaterialUpdateRequest request) {
        return ApiResponse.success(studyMaterialService.update(memberId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        studyMaterialService.delete(memberId, id);
        return ResponseEntity.noContent().build();
    }
}
