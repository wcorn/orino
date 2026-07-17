package ds.project.orino.planner.dataset.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.dataset.dto.AddColumnRequest;
import ds.project.orino.planner.dataset.dto.BulkRowsRequest;
import ds.project.orino.planner.dataset.dto.CreateDatasetRequest;
import ds.project.orino.planner.dataset.dto.DatasetResponse;
import ds.project.orino.planner.dataset.dto.InsertRowRequest;
import ds.project.orino.planner.dataset.dto.InsertRowResponse;
import ds.project.orino.planner.dataset.dto.RenameColumnRequest;
import ds.project.orino.planner.dataset.dto.ReorderColumnsRequest;
import ds.project.orino.planner.dataset.dto.ResizeColumnRequest;
import ds.project.orino.planner.dataset.dto.RowView;
import ds.project.orino.planner.dataset.dto.RowsResponse;
import ds.project.orino.planner.dataset.dto.UpdateRowRequest;
import ds.project.orino.planner.dataset.service.DatasetService;
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

/**
 * 데이터 그리드 블록의 표 데이터 저장소 API. 노트 content와 분리된 dataset 리소스.
 */
@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DatasetResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody CreateDatasetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(datasetService.create(memberId, request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<DatasetResponse> meta(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        return ApiResponse.success(datasetService.getMeta(memberId, id));
    }

    /** 데이터셋 삭제(노트에서 datasetTable 블록 제거 시). 행은 cascade로 함께 삭제. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        datasetService.delete(memberId, id);
        return ResponseEntity.noContent().build();
    }

    /** 열 추가(끝에). key는 서버가 발급하며 기존 행은 건드리지 않는다. */
    @PostMapping("/{id}/columns")
    public ResponseEntity<ApiResponse<DatasetResponse>> addColumn(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody AddColumnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(datasetService.addColumn(memberId, id, request)));
    }

    /** 열 삭제. 마지막 열은 지울 수 없다(400). */
    @DeleteMapping("/{id}/columns/{key}")
    public ApiResponse<DatasetResponse> deleteColumn(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @PathVariable String key) {
        return ApiResponse.success(datasetService.deleteColumn(memberId, id, key));
    }

    /** 열 순서 변경. 전체 순서를 받으며 현재 열 집합과 정확히 같아야 한다. */
    @PatchMapping("/{id}/columns/order")
    public ApiResponse<DatasetResponse> reorderColumns(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody ReorderColumnsRequest request) {
        return ApiResponse.success(datasetService.reorderColumns(memberId, id, request));
    }

    /** 한 셀의 수식을 그 열 전체에 채운다(계산 열 만들기). */
    @PostMapping("/{id}/columns/{key}/fill")
    public ApiResponse<DatasetResponse> fillDownColumn(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @PathVariable String key,
            @RequestParam int fromRowIndex) {
        return ApiResponse.success(
                datasetService.fillDownColumn(memberId, id, key, fromRowIndex));
    }

    /** 열 너비 변경(px). 열 단위 표시 속성이라 행은 건드리지 않는다. */
    @PatchMapping("/{id}/columns/{key}/width")
    public ApiResponse<DatasetResponse> resizeColumn(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @PathVariable String key,
            @Valid @RequestBody ResizeColumnRequest request) {
        return ApiResponse.success(datasetService.resizeColumn(memberId, id, key, request));
    }

    /** 열 너비 초기화 — 기본 폭으로 되돌린다. */
    @DeleteMapping("/{id}/columns/{key}/width")
    public ApiResponse<DatasetResponse> resetColumnWidth(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @PathVariable String key) {
        return ApiResponse.success(datasetService.resetColumnWidth(memberId, id, key));
    }

    /** 열 이름 변경. key는 불변이며 label만 바꾼다. */
    @PatchMapping("/{id}/columns/{key}")
    public ApiResponse<DatasetResponse> renameColumn(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @PathVariable String key,
            @Valid @RequestBody RenameColumnRequest request) {
        return ApiResponse.success(datasetService.renameColumn(memberId, id, key, request));
    }

    /** Import 청크 — 행을 끝에 벌크 추가. */
    @PostMapping("/{id}/rows/bulk")
    public ApiResponse<DatasetResponse> bulkAppend(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody BulkRowsRequest request) {
        return ApiResponse.success(datasetService.bulkAppend(memberId, id, request));
    }

    @GetMapping("/{id}/rows")
    public ApiResponse<RowsResponse> rows(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(datasetService.getRows(memberId, id, offset, limit));
    }

    @PatchMapping("/{id}/rows/{rowIndex}")
    public ApiResponse<RowView> updateRow(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @PathVariable int rowIndex,
            @Valid @RequestBody UpdateRowRequest request) {
        return ApiResponse.success(datasetService.updateRow(memberId, id, rowIndex, request));
    }

    @PostMapping("/{id}/rows")
    public ResponseEntity<ApiResponse<InsertRowResponse>> insertRow(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody InsertRowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(datasetService.insertRow(memberId, id, request)));
    }

    @DeleteMapping("/{id}/rows/{rowIndex}")
    public ResponseEntity<Void> deleteRow(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @PathVariable int rowIndex) {
        datasetService.deleteRow(memberId, id, rowIndex);
        return ResponseEntity.noContent().build();
    }
}
