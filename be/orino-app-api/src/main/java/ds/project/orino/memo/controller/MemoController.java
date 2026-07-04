package ds.project.orino.memo.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.memo.dto.MemoCreateRequest;
import ds.project.orino.memo.dto.MemoDetailResponse;
import ds.project.orino.memo.dto.MemoTreeResponse;
import ds.project.orino.memo.dto.MemoUpdateRequest;
import ds.project.orino.memo.dto.MemoUpdateResponse;
import ds.project.orino.memo.service.MemoService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memos")
public class MemoController {

    private final MemoService memoService;

    public MemoController(MemoService memoService) {
        this.memoService = memoService;
    }

    @GetMapping
    public ApiResponse<MemoTreeResponse> tree(
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(memoService.findTree(memberId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MemoDetailResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody MemoCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(memoService.create(memberId, request)));
    }

    @GetMapping("/{memoId}")
    public ApiResponse<MemoDetailResponse> detail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long memoId) {
        return ApiResponse.success(memoService.findOne(memberId, memoId));
    }

    @PatchMapping("/{memoId}")
    public ApiResponse<MemoUpdateResponse> update(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long memoId,
            @Valid @RequestBody MemoUpdateRequest request) {
        return ApiResponse.success(memoService.update(memberId, memoId, request));
    }

    @DeleteMapping("/{memoId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long memoId) {
        memoService.delete(memberId, memoId);
        return ResponseEntity.noContent().build();
    }
}
