package ds.project.orino.planner.note.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.note.dto.NoteCreateRequest;
import ds.project.orino.planner.note.dto.NoteDetailResponse;
import ds.project.orino.planner.note.dto.NoteTreeResponse;
import ds.project.orino.planner.note.dto.NoteUpdateRequest;
import ds.project.orino.planner.note.dto.NoteUpdateResponse;
import ds.project.orino.planner.note.service.NoteService;
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
 * 통합 노트 API. materialId 쿼리로 자료 종속 노트(값 있음)와 독립 노트(생략)를 구분한다.
 */
@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public ApiResponse<NoteTreeResponse> tree(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) Long materialId) {
        return ApiResponse.success(noteService.findTree(memberId, materialId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NoteDetailResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) Long materialId,
            @Valid @RequestBody NoteCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(noteService.create(memberId, materialId, request)));
    }

    @GetMapping("/{noteId}")
    public ApiResponse<NoteDetailResponse> detail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long noteId) {
        return ApiResponse.success(noteService.findOne(memberId, noteId));
    }

    @PatchMapping("/{noteId}")
    public ApiResponse<NoteUpdateResponse> update(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long noteId,
            @Valid @RequestBody NoteUpdateRequest request) {
        return ApiResponse.success(noteService.update(memberId, noteId, request));
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long noteId) {
        noteService.delete(memberId, noteId);
        return ResponseEntity.noContent().build();
    }
}
