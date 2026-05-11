package ds.project.orino.planner.material.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.material.dto.UnitCreateRequest;
import ds.project.orino.planner.material.dto.UnitListResponse;
import ds.project.orino.planner.material.dto.UnitResponse;
import ds.project.orino.planner.material.dto.UnitUpdateRequest;
import ds.project.orino.planner.material.service.StudyUnitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planner")
public class StudyUnitController {

    private final StudyUnitService studyUnitService;

    public StudyUnitController(StudyUnitService studyUnitService) {
        this.studyUnitService = studyUnitService;
    }

    @PostMapping("/materials/{materialId}/units")
    public ResponseEntity<ApiResponse<UnitListResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long materialId,
            @Valid @RequestBody UnitCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(new UnitListResponse(
                        studyUnitService.create(memberId, materialId, request))));
    }

    @PatchMapping("/units/{id}")
    public ApiResponse<UnitResponse> update(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody UnitUpdateRequest request) {
        return ApiResponse.success(studyUnitService.update(memberId, id, request));
    }

    @DeleteMapping("/units/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        studyUnitService.delete(memberId, id);
        return ResponseEntity.noContent().build();
    }
}
