package ds.project.orino.planner.note.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.note.dto.NoteResponse;
import ds.project.orino.planner.note.dto.NoteUpdateRequest;
import ds.project.orino.planner.note.dto.NoteUpdateResponse;
import ds.project.orino.planner.note.service.NoteService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planner/materials/{materialId}/note")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public ApiResponse<NoteResponse> get(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long materialId) {
        return ApiResponse.success(noteService.findByMaterialId(memberId, materialId));
    }

    @PutMapping
    public ApiResponse<NoteUpdateResponse> put(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long materialId,
            @Valid @RequestBody NoteUpdateRequest request) {
        return ApiResponse.success(noteService.update(memberId, materialId, request.content()));
    }
}
