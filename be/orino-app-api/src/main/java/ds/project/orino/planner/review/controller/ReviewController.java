package ds.project.orino.planner.review.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.review.dto.ReviewCompletionRequest;
import ds.project.orino.planner.review.dto.ReviewCompletionResponse;
import ds.project.orino.planner.review.service.ReviewCompletionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planner/reviews")
public class ReviewController {

    private final ReviewCompletionService reviewCompletionService;

    public ReviewController(ReviewCompletionService reviewCompletionService) {
        this.reviewCompletionService = reviewCompletionService;
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<ReviewCompletionResponse> complete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody ReviewCompletionRequest request) {
        return ApiResponse.success(reviewCompletionService.complete(memberId, id, request));
    }
}
