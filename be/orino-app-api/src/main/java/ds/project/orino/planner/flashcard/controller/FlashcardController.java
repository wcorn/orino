package ds.project.orino.planner.flashcard.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.flashcard.dto.FlashcardCreateRequest;
import ds.project.orino.planner.flashcard.dto.FlashcardCreateResponse;
import ds.project.orino.planner.flashcard.dto.FlashcardListResponse;
import ds.project.orino.planner.flashcard.dto.FlashcardResponse;
import ds.project.orino.planner.flashcard.dto.FlashcardUpdateRequest;
import ds.project.orino.planner.flashcard.service.FlashcardService;
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
@RequestMapping("/api/planner")
public class FlashcardController {

    private final FlashcardService flashcardService;

    public FlashcardController(FlashcardService flashcardService) {
        this.flashcardService = flashcardService;
    }

    /** 자료의 카드 목록 — created_at 커서 keyset 페이징 + 검색(q)/종류/복습 상태/정렬 필터. */
    @GetMapping("/materials/{materialId}/flashcards")
    public ApiResponse<FlashcardListResponse> list(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long materialId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "all") String review,
            @RequestParam(defaultValue = "created_asc") String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success(flashcardService.findByMaterialId(
                memberId, materialId, q, type, review, sort, cursor, size));
    }

    @PostMapping("/materials/{materialId}/flashcards")
    public ResponseEntity<ApiResponse<FlashcardCreateResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long materialId,
            @Valid @RequestBody FlashcardCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(flashcardService.create(memberId, materialId, request)));
    }

    @PatchMapping("/flashcards/{id}")
    public ApiResponse<FlashcardResponse> update(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody FlashcardUpdateRequest request) {
        return ApiResponse.success(flashcardService.update(memberId, id, request));
    }

    @DeleteMapping("/flashcards/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        flashcardService.delete(memberId, id);
        return ResponseEntity.noContent().build();
    }
}
